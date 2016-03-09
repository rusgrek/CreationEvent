package ru.infor.common.application;

import ru.infor.common.core.ConfigProperties;

public class Import_App {

	
	public Import_App() {
		boolean err = false;
		if (ConfigProperties.getProperty("vms-ws.soap.services.url", "").equals("")) {
			System.out.println("Ошибка. Нет строки подключения к сервисам.");
			err = true;
		}

		
		if(!err){
			@SuppressWarnings("unused")
			ImportData importData = new ImportData();
		}
		 
	}

	public static void main(String[] args) {
		new Import_App();

	}
}
