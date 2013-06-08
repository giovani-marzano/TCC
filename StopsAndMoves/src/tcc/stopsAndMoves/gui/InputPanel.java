package tcc.stopsAndMoves.gui;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.TitledBorder;

import weka.core.converters.Loader;
import weka.gui.ConverterFileChooser;

public class InputPanel extends JPanel {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private Loader loader = null;
	
	private ConverterFileChooser fileChooser = new ConverterFileChooser();
	private ImageIcon okIcon = null;
	private ImageIcon notOkIcon = null;
	private JLabel lblOkIcon;
	private TitledBorder border;
	
	/**
	 * Create the panel.
	 */
	public InputPanel() {
		border = new TitledBorder(null, "<Title>", TitledBorder.LEADING, TitledBorder.TOP, null, null);
		setBorder(border);
		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		
		JButton btnArquivo = new JButton(Messages.getString("InputPanel.btnArquivo.text")); //$NON-NLS-1$
		btnArquivo.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				createLoaderFromFile();
			}
		});
		add(btnArquivo);
		
		Component horizontalStrut = Box.createHorizontalStrut(5);
		add(horizontalStrut);
		
		JButton btnSql = new JButton(Messages.getString("InputPanel.btnSql.text")); //$NON-NLS-1$
		btnSql.setEnabled(false);
		add(btnSql);
		
		Component horizontalStrut_1 = Box.createHorizontalStrut(5);
		add(horizontalStrut_1);
		
		notOkIcon = new ImageIcon(InputPanel.class.getResource("/tcc/stopsAndMoves/gui/img/notOk.png"));
		okIcon = new ImageIcon(InputPanel.class.getResource("/tcc/stopsAndMoves/gui/img/Ok.png"));
		
		lblOkIcon = new JLabel("");
		lblOkIcon.setIcon(notOkIcon);
		lblOkIcon.setHorizontalAlignment(SwingConstants.CENTER);
		add(lblOkIcon);
	}
	
	private void createLoaderFromFile() {
		if ( fileChooser.showOpenDialog(this.getParent())
				== JFileChooser.APPROVE_OPTION ) {
			setLoader(fileChooser.getLoader());
		}
	}

	public Loader getLoader() {
		return loader;
	}

	public void setLoader(Loader loader) {
		if ( loader != null ) {
			lblOkIcon.setIcon(okIcon);
		}
		else {
			lblOkIcon.setIcon(notOkIcon);
		}
		this.loader = loader;
	}
	
	public String getTitle() {
		return border.getTitle();
	}
	
	public void setTitle( String title) {
		border.setTitle(title);
	}
	
	public void reset() {
		setLoader(null);
	}
}
