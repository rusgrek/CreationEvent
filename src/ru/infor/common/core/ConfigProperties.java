package ru.infor.common.core;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.util.Properties;

public class ConfigProperties {

	static Properties props = new Properties();

	static {
		reloadProperties();
	}

	public static String getProperty(String name, String defaultValue) {
		String value = props.getProperty(name);

		if (value == null)
			value = System.getProperty(name, defaultValue);

		return value;
	}

	public static void reloadProperties() {
		Properties p = new Properties();
		loadProps(p, ".prop");

		synchronized (props) {
			props = new Properties(p);
		}
	}

	public static void loadProps(Properties props, final String fileExtension) {
		String folder = System.getProperty("common.props.folder", "config");
		File file = new File(folder);
		if (file.exists())

			if (file.isDirectory()) {
				FileFilter ff = new FileFilter() {
					public boolean accept(File pathname) {
						return pathname.getAbsolutePath().endsWith(
								fileExtension);
					}
				};

				for (File f : file.listFiles(ff))
					loadPropertiesFromFile(f, props);

			} else
				loadPropertiesFromFile(file, props);
	}

	private static void loadPropertiesFromFile(File file, Properties props) {
		Properties p = new Properties();
		try {
			p.load(new FileInputStream(file));
		} catch (Exception e) {
		}
		props.putAll(p);
	}

}
