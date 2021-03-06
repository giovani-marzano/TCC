package tcc.stopsAndMoves.converters.sql;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;

import tcc.stopsAndMoves.Application;
import tcc.stopsAndMoves.Move;
import tcc.stopsAndMoves.SamplePoint;
import tcc.stopsAndMoves.SimplePoint;
import tcc.stopsAndMoves.SpatialFeature;
import tcc.stopsAndMoves.Stop;
import tcc.stopsAndMoves.Trajectory;
import tcc.stopsAndMoves.converters.ITrajectoryLoader;
import tcc.stopsAndMoves.converters.ITrajectorySaver;

public class TrajectorySql implements ITrajectoryLoader, ITrajectorySaver {

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getDatabaseURL() {
		return databaseURL;
	}

	public void setDatabaseURL(String databaseURL) {
		this.databaseURL = databaseURL;
	}

	public String getStopsCmd() {
		return stopsCmd;
	}

	public void setStopsCmd(String stopsCmd) {
		this.stopsCmd = stopsCmd;
	}

	public String getMovesCmd() {
		return movesCmd;
	}

	public void setMovesCmd(String movesCmd) {
		this.movesCmd = movesCmd;
	}

	public String getMovePointsCmd() {
		return movePointsCmd;
	}

	public void setMovePointsCmd(String movePointsCmd) {
		this.movePointsCmd = movePointsCmd;
	}

	private String userName;
	private String password;
	private String databaseURL;

	public final String TID_COL = "tid";
	public final String TIME_COL = "time";
	public final String GEOM_COL = "geom";
	public final String SFID_COL = "sfid";
	public final String SFTIME_COL = "time";
	public final String SFGEOM_COL = "geom";
	public final String SFPTIDX_COL = "ptidx";

	// Consultas SQL para carregar os dados necessários.
	private String trajectoryPointsQuery = "SELECT tid, timestmp as time, ST_AsText(the_geom) as geom"
			+ " FROM trajetoria ORDER BY tid, time";
	private String spatialFeaturesQuery = "select gid as sfid, 120 as time, ST_AsText(the_geom) as geom from regiao";

	// Comandos SQL para a gravação dos dados gerados
	private String stopsCmd = "INSERT INTO stop (tid, sid, sfid, intime, outtime)" + " VALUES (?,?,?,?,?)";
	private String movesCmd = "INSERT INTO move (tid, mid, storig, stdest)" + " VALUES (?,?,?,?)";
	private String movePointsCmd = "INSERT INTO movept (tid, mid, pttime, pos)" + " VALUES (?,?,?,ST_SetSRID(ST_MakePoint(?,?),4326))";;

	private List<Trajectory> trajectories;
	private Iterator<Trajectory> trjIter = null;

	private Map<Long, SpatialFeature> regionSet;

	@Override
	public void writeStopsAndMoves(Trajectory trj) throws IOException {
		int count = 0;

		try (Connection conn = getConnection()) {
			conn.setAutoCommit(false);
			try (PreparedStatement pst = conn.prepareStatement(stopsCmd)) {
				count = 0;
				for (Stop stop : trj.getStops()) {
					pst.setLong(1, trj.getId());
					pst.setLong(2, stop.getId());
					pst.setLong(3, stop.getSpatialFeature().getId());
					pst.setTimestamp(4, new Timestamp((long) (stop.getEnterTime() * 1000)));
					pst.setTimestamp(5, new Timestamp((long) (stop.getLeaveTime() * 1000)));

					pst.addBatch();

					if (++count % 1000 == 0) {
						pst.executeBatch();
					}
				}
				pst.executeBatch();
				conn.commit();
			}
			try (PreparedStatement pst = conn.prepareStatement(movesCmd)) {
				count = 0;
				for (Move move : trj.getMoves()) {
					pst.setLong(1, trj.getId());
					pst.setLong(2, move.getId());
					Stop stop = move.getOrigin();
					if (stop != null) {
						pst.setLong(3, stop.getId());
					} else {
						pst.setNull(3, java.sql.Types.INTEGER);
					}
					stop = move.getDestination();
					if (stop != null) {
						pst.setLong(4, stop.getId());
					} else {
						pst.setNull(4, java.sql.Types.INTEGER);
					}
					pst.addBatch();

					if (++count % 1000 == 0) {
						pst.executeBatch();
					}
				}
				pst.executeBatch();
				conn.commit();
			}
			try (PreparedStatement pst = conn.prepareStatement(movePointsCmd)) {
				count = 0;
				for (Move move : trj.getMoves()) {
					for (SamplePoint sp: move.getPath()) {
						pst.setLong(1, trj.getId());
						pst.setLong(2, move.getId());						
						pst.setTimestamp(3, new Timestamp((long) (sp.getTime() * 1000)));
						pst.setDouble(4, sp.getLon());
						pst.setDouble(5, sp.getLat());
					}
					pst.addBatch();

					if (++count % 1000 == 0) {
						pst.executeBatch();
					}
				}
				pst.executeBatch();
				conn.commit();
			}
			conn.setAutoCommit(true);
		} catch (SQLException ex) {
			while (ex != null) {
				System.out.println(ex.getLocalizedMessage());
				ex = ex.getNextException();
			}			
			throw new IOException(ex);
		}
	}

	@Override
	public Trajectory getNextTrajectory() throws IOException {

		if (trjIter == null) {
			try {
				trjIter = loadTrajectories();
			} catch (SQLException ex) {
				System.out.println(ex.toString());
				throw new IOException(ex);
			} catch (ParseException ex) {
				System.out.println(ex.toString());
				throw new IOException(ex);
			}
		}

		if (trjIter != null && trjIter.hasNext()) {
			return trjIter.next();
		} else {
			return null;
		}
	}

	private Iterator<Trajectory> loadTrajectories() throws SQLException, ParseException {
		Connection conn = this.getConnection();

		trajectories = new ArrayList<>();
		Trajectory trj = null;
		
		WKTReader wktr = new WKTReader();

		try (Statement stm = conn.createStatement();) {
			ResultSet rs = stm.executeQuery(trajectoryPointsQuery);
			while (rs.next()) {
				long tid = rs.getInt(TID_COL);

				if (trj == null || trj.getId() != tid) {
					trj = new Trajectory(tid);
					trajectories.add(trj);
				}

				Timestamp time = rs.getTimestamp(TIME_COL);
				String geom = rs.getString(GEOM_COL);

				SimplePoint pt = new SimplePoint((Point) wktr.read(geom), time.getTime()/1000.0);
				trj.add(pt);
			}
		}

		conn.close();

		return trajectories.iterator();
	}

	private void loadSpatialFeatures() throws SQLException, ParseException {
		Connection conn = this.getConnection();

		regionSet = new HashMap<Long, SpatialFeature>();

		try (Statement stm = conn.createStatement();) {
			ResultSet rs = stm.executeQuery(spatialFeaturesQuery);
			while (rs.next()) {
				long sfid = rs.getLong(SFID_COL);
				double time = rs.getDouble(SFTIME_COL);
				String geom = rs.getString(SFGEOM_COL);

				SpatialFeature sf = new SpatialFeature(sfid);
				WKTReader wktr = new WKTReader();
				sf.setArea( (Polygon) wktr.read(geom));
				sf.setMinimunTime(time);
				regionSet.put(sf.getId(), sf);
			}
		}

		conn.close();
	}

	public Application loadApplication() throws SQLException, ParseException {
		loadSpatialFeatures();

		return new Application(new HashSet<>(regionSet.values()));
	}

	private Connection getConnection() throws SQLException {

		Connection conn = null;
		Properties connectionProps = new Properties();
		connectionProps.put("user", this.userName);
		connectionProps.put("password", this.password);

		conn = DriverManager.getConnection(databaseURL, connectionProps);

		System.out.println("Connected to database");
		return conn;
	}
}
