package ch.ehi.ili2fgdb;

import ch.ehi.basics.logging.EhiLogger;
import javax.swing.*;

import ch.ehi.ili2db.base.Ili2db;
import ch.ehi.ili2db.base.Ili2dbException;
import ch.ehi.ili2db.gui.*;

public class FgdbDbPanelDescriptor extends AbstractDbPanelDescriptor {
    
    public JPanel createPanel() {
		return new FgdbDbPanel();
	}
	public void aboutToDisplayPanel() {
		super.aboutToDisplayPanel();
    	Config config=((Ili2dbWizard)getWizard()).getIli2dbConfig();
        FgdbDbPanel panel=(FgdbDbPanel)getPanelComponent();
    	panel.setDbname(config.getDbdatabase());
    	panel.setDbhost(config.getDbhost());
    	panel.setDbport(config.getDbport());
    	panel.setDbusr(config.getDbusr());
    	panel.setDbpwd(config.getDbpwd());
    	panel.setDbUrlConverter(((Ili2dbWizard)getWizard()).getDbUrlConverter());
    	panel.setJdbcDriver(config.getJdbcDriver());
	}
	public void aboutToHidePanel() {
    	Config config=((Ili2dbWizard)getWizard()).getIli2dbConfig();
        FgdbDbPanel panel=(FgdbDbPanel)getPanelComponent();
    	config.setDbdatabase(panel.getDbname());
    	config.setDbhost(panel.getDbhost());
    	config.setDbport(panel.getDbport());
    	config.setDbusr(panel.getDbusr());
    	config.setDbpwd(panel.getDbpwd());
    	try {
			config.setDburl(panel.getDbUrlConverter().makeUrl(config));
			Ili2db.readSettingsFromDb(config);
		} catch (Ili2dbException e) {
			EhiLogger.logError(e);
		}
		super.aboutToHidePanel();
	}

    
    
}
