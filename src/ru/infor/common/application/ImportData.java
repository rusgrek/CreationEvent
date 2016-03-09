package ru.infor.common.application;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import ru.infor.client.ServicesConnection;
import ru.infor.client.soap.proxy.vms.EventData4RadioBeaconWSProxy;
import ru.infor.client.soap.proxy.vms.EventWSProxy;
import ru.infor.client.soap.proxy.vms.ImportanceEventWSProxy;
import ru.infor.client.soap.proxy.vms.IncidentWSProxy;
import ru.infor.client.soap.proxy.vms.NDDataWSProxy;
import ru.infor.client.soap.proxy.vms.NavigationDeviceWSProxy;
import ru.infor.client.soap.proxy.vms.PoliceTaskExecWSProxy;
import ru.infor.client.soap.proxy.vms.PoliceTaskTypeWSProxy;
import ru.infor.client.soap.proxy.vms.PoliceTaskWSProxy;
import ru.infor.client.soap.proxy.vms.ReactionTypeWSProxy;
import ru.infor.client.soap.proxy.vms.RecordTypeWSProxy;
import ru.infor.common.SecurityUtils;
import ru.infor.common.core.ConfigProperties;
import ru.infor.ws.objects.InvocationContext;
import ru.infor.ws.objects.SearchResultList;
import ru.infor.ws.objects.core.DirectoriesSearchCriteria;
import ru.infor.ws.objects.events.entities.Event;
import ru.infor.ws.objects.events.entities.EventData4RadioBeacon;
import ru.infor.ws.objects.events.entities.ImportanceEvent;
import ru.infor.ws.objects.events.entities.ReactionType;
import ru.infor.ws.objects.police.entities.Incident;
import ru.infor.ws.objects.police.entities.PoliceTask;
import ru.infor.ws.objects.police.entities.PoliceTaskExec;
import ru.infor.ws.objects.police.entities.PoliceTaskType;
import ru.infor.ws.objects.vms.NavigationDeviceSearchCriteria;
import ru.infor.ws.objects.vms.entities.NDData;
import ru.infor.ws.objects.vms.entities.NavigationDevice;
import ru.infor.ws.objects.vms.entities.RecordType;

public class ImportData {
	InvocationContext context = new InvocationContext();
	ServicesConnection conn;
	NavigationDeviceWSProxy deviceProxy;
	NavigationDeviceSearchCriteria ndSc;
	NDDataWSProxy proxyNDData;
	RecordTypeWSProxy proxyRT;
	EventWSProxy eventProxy;
	ImportanceEventWSProxy importanceEventProxy;
	ReactionTypeWSProxy reactionTypeProxy;
	EventData4RadioBeaconWSProxy dataProxy;
	PoliceTaskWSProxy policeTaskWSProxy;
	PoliceTaskTypeWSProxy policeTaskTypeWSProxy;
	PoliceTaskExecWSProxy policeTaskExecWSProxy;
	IncidentWSProxy incidentWSProxy;
	Date lastDate;
	String radioBeanCode;
	SimpleDateFormat df = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
	
	public ImportData() {
		// createLogFileName();

		System.setProperty("common.props.folder", "config");
		conn = ServicesConnection.getConnection("vms-ws");
		context.setUserName("admin");
		context.setPassword(SecurityUtils.encrypt("adminadmin".getBytes()));
		context.setInitiator("Test creattion event (Java) ");
		writeLog("Current settings (ver. 1.0): ", 1);
		writeLog("       vms-ws.soap.services.url = " + ConfigProperties.getProperty("vms-ws.soap.services.url", ""),
				1);

		incidentWSProxy = new IncidentWSProxy(conn);
		policeTaskExecWSProxy = new PoliceTaskExecWSProxy(conn);
		policeTaskTypeWSProxy = new PoliceTaskTypeWSProxy(conn);
		policeTaskWSProxy = new PoliceTaskWSProxy(conn);
		dataProxy = new EventData4RadioBeaconWSProxy(conn);
		reactionTypeProxy = new ReactionTypeWSProxy(conn);
		importanceEventProxy = new ImportanceEventWSProxy(conn);
		eventProxy = new EventWSProxy(conn);
		proxyRT = new RecordTypeWSProxy(conn);
		proxyNDData = new NDDataWSProxy(conn);
		deviceProxy = new NavigationDeviceWSProxy(conn);
		ndSc = new NavigationDeviceSearchCriteria();

		radioBeanCode = ConfigProperties.getProperty("radio.bean.code", "");
		lastDate = new Date();

		ndSc.setImei("*" + radioBeanCode + "*");
		SearchResultList sr = deviceProxy.getList(context, ndSc);
		if (sr.isEmpty()) {
			// не нашли устройство с уник.номером
			writeLog("No device with code: " + radioBeanCode, 1);
		}
		NavigationDevice nd = (NavigationDevice) sr.getObjList()[0];
		Double lat = 55.826195D;
		Double lon = 37.637550D;
		String coor = ConfigProperties.getProperty("radio.bean.coordinates", "");
		if (!coor.trim().equals("")) {
			String[] coo = coor.split(",");
			if (coo != null && coo.length == 2) {
				lat = Double.valueOf(coo[0]);
				lon = Double.valueOf(coo[1]);
			}
		}

		NDData ndd = saveNDData(context, nd.getId(), lastDate, lat, lon);
		if (ndd == null || ndd.getId().compareTo(Long.valueOf(0)) < 1) {
			// не сохранилась nddata
			writeLog("Problems while saving nddata", 1);
			return;
		}
		Event event = createEvent(context, ndd, nd.getName(), "193", "273");
		if (event == null || event.getId() == null) {
			// не сохранилась событие
			writeLog("Problems while saving event", 1);
			return;
		}

		EventData4RadioBeacon data = createEventData4RadioBeacon(context, event.getId(), getEventData());
		if (data == null || data.getId() == null) {
			// не сохранилось письмо
			writeLog("Problems while saving eventdata", 1);
			return;
		}
			
		writeLog("Create event from radiobeacon!", 1);
			
		if (!createPoliceTask(context))
			return;

		writeLog("Create event from ERA-Glonass!", 1);

	}

	private boolean createPoliceTask(InvocationContext ctx) {
		PoliceTask task = new PoliceTask();
		task.setExternalId("19176B5B29175863E0538B121EAC0007");
		task.setExternalCode("0007");
		task.setStartAt(new Date());
		task.setTerminalPhone("9418204860");

		Double lat = 55.826195D;
		Double lon = 37.637550D;
		String coor = ConfigProperties.getProperty("era.coordinates", "");
		if (!coor.trim().equals("")) {
			String[] coo = coor.split(",");
			if (coo != null && coo.length == 2) {
				lat = Double.valueOf(coo[0]);
				lon = Double.valueOf(coo[1]);
			}
		}
		task.setLat(lat);
		task.setLon(lon);

		task.setDescription(createDescription());
		task.setSource("ЭРА");
		task.setType(getPoliceTaskType(ctx));// Тип задачи - Происшествие
		task.setExec(getPoliceTaskExec(ctx));// Статус происшествия - Новое
		task.setIncident(getIncident(ctx));// Происшествие - ДТП

		task = policeTaskWSProxy.save(ctx, task);

		return true;
	}

	private String createDescription() {
		String str = "ТС: В419МК777\n";
		str = str.concat("Марка/Модель ТС: Nissan X-Trail\n");
		str = str.concat("Тип ТС: пассажирский (Class M1)\n");
		str = str.concat("Адрес: пр-т Мира, 119, Москва\n");
		str = str.concat("Цвет кузова ТС: Бежевый\n");
		str = str.concat("Тип активации вызова: вручную\n");
		str = str.concat("Идентификатор ТС по ISO 3779: Х1М3205СХА0000007\n");
		str = str.concat("Тип удара: \n");
		str = str.concat(" - Удар справа\n");

		return str;
	}


	private Incident getIncident(InvocationContext ctx) {
		Incident res = null;
		DirectoriesSearchCriteria sc = new DirectoriesSearchCriteria();
		sc.setDescription("*ДТП*");
		SearchResultList srList = incidentWSProxy.getList(ctx, sc);
		if (srList != null && !srList.isEmpty()) {
			res = (Incident) srList.getObjList()[0];
		} else {
			res = new Incident();
			res.setDescription("ДТП");
			res.setCode("150");
			res = incidentWSProxy.save(ctx, res);
		}
		return res;
	}

	private PoliceTaskExec getPoliceTaskExec(InvocationContext ctx) {
		PoliceTaskExec res = null;
		DirectoriesSearchCriteria sc = new DirectoriesSearchCriteria();
		sc.setDescription("*Новое*");
		SearchResultList srList = policeTaskExecWSProxy.getList(ctx, sc);
		if (srList != null && !srList.isEmpty()) {
			res = (PoliceTaskExec) srList.getObjList()[0];
		} else {
			res = new PoliceTaskExec();
			res.setDescription("Новое");
			res.setCode("Н");
			res = policeTaskExecWSProxy.save(ctx, res);
		}
		return res;
	}

	private PoliceTaskType getPoliceTaskType(InvocationContext ctx) {
		PoliceTaskType res = null;
		DirectoriesSearchCriteria sc = new DirectoriesSearchCriteria();
		sc.setDescription("*Происшествие*");
		SearchResultList srList = policeTaskTypeWSProxy.getList(ctx, sc);
		if (srList != null && !srList.isEmpty()) {
			res = (PoliceTaskType) srList.getObjList()[0];
		} else {
			res = new PoliceTaskType();
			res.setDescription("Происшествие");
			res.setCode("Ж");
			res = policeTaskTypeWSProxy.save(ctx, res);
		}
		return res;
	}

	private EventData4RadioBeacon createEventData4RadioBeacon(InvocationContext sic, Long id, String str) {
		EventData4RadioBeacon data = new EventData4RadioBeacon();
		data.setEventId(id);
		data.setData(str.getBytes());
		data = dataProxy.save(sic, data);
		return data;
	}


	private Event createEvent(InvocationContext ctx, NDData data, String serialNo, String certificateNo,
			String country) {
		String message = "Поступил сигнал радиомаяка (серийный: ".concat(serialNo).concat(", сертификат: ")
				.concat(certificateNo).concat(", страна: ").concat(country).concat(")");
		Event event = new Event();
		event.setEventDate(data.getCreatedDateTime());
		event.setInitiator(data.getDeviceId());
		event.setNddataId(data.getId());
		event.setSpeed(data.getSpeed());
		event.setMessage(message);
		event.setActive(1);
		event.setImportanceEvent(getImportanceEvent(ctx));
		event.setReactionType(getReactionType(ctx));
		event = eventProxy.save(ctx, event);
		return event;
	}

	private ImportanceEvent getImportanceEvent(InvocationContext ctx) {
		ImportanceEvent res = null;
		DirectoriesSearchCriteria sc = new DirectoriesSearchCriteria();
		sc.setDescription("*тревога*");
		SearchResultList srList = importanceEventProxy.getList(ctx, sc);
		if (srList != null && !srList.isEmpty()) {
			res = (ImportanceEvent) srList.getObjList()[0];
		} else {
			res = new ImportanceEvent();
			res.setDescription("тревога");
			res.setCode("90");
			res = importanceEventProxy.save(ctx, res);
		}
		return res;
	}

	private ReactionType getReactionType(InvocationContext ctx) {
		ReactionType res = null;
		DirectoriesSearchCriteria sc = new DirectoriesSearchCriteria();
		sc.setDescription("*не требуется вмешательства*");
		SearchResultList srList = reactionTypeProxy.getList(ctx, sc);
		if (srList != null && !srList.isEmpty()) {
			res = (ReactionType) srList.getObjList()[0];
		} else {
			res = new ReactionType();
			res.setDescription("не требуется вмешательства");
			res.setCode("00");
			res = reactionTypeProxy.save(ctx, res);
		}
		return res;
	}

	private NDData saveNDData(InvocationContext ctx, Long deviceid, Date nddataDate, Double lat, Double lon) {
		NDData d = new NDData();
		RecordType rt = getRecordType(ctx);
		d.setType(rt);
		d.setDeviceId(deviceid);
		d.setCreatedDateTime(nddataDate);
		d.setLat(lat);
		d.setLon(lon);
		d.setSpeed(0.0D);
		d.setTripIndex(0);
		d.setAlarmDevice(0);
		NDData[] list = proxyNDData.saveList(ctx, new NDData[] { d });
		return list[0];
	}

	private RecordType getRecordType(InvocationContext ctx) {
		DirectoriesSearchCriteria dSC = new DirectoriesSearchCriteria();
		dSC.setDescription("*по времени*");
		SearchResultList dSR = proxyRT.getList(ctx, dSC);
		return (RecordType) dSR.getObjList()[0];
	}


	public boolean writeToFile(String fileName, String dataLine) {
		DataOutputStream dos;

		try {
			File outFile = new File(fileName);
			dos = new DataOutputStream(new FileOutputStream(outFile));

			dos.writeBytes(dataLine);
			dos.close();
		} catch (FileNotFoundException ex) {
			System.out.println(ex.getMessage());
			return (false);
		} catch (IOException ex) {
			System.out.println(ex.getMessage());
			return (false);
		}
		return (true);
	}


	protected void writeLog(String value) {
		writeLog(value, true);

	}

	protected void writeLog(String value, int sysout) {
		writeLog(value, true, sysout);
	}

	private void writeLog(String value, boolean newString) {
		writeLog(value, true, 0);
	}

	private void writeLog(String value, boolean newString, int sysout) {
		String str = df.format(new Date()) + ":  "
				+ ((value == null) ? "" : value);
		if (sysout == 1)
			System.out.println(str);
	}

	private String getEventData() {
		String str = "DATE: 02 JUL 15 1111Z FROM: MCC RUSSIA \n" + "TO  : RCC RUSSIA \n"
				+ "FORMAT FILE: RCC406E.FMT \n" + "1.  DISTRESS COSPAS-SARSAT INITIAL (ENCODED) ALERT \n"
				+ "2.  MSG NO: 51479  CMC REF: 222E60801EFFBFF \n"
				+ "3.  DETECTED AT: 02 JUL 15  1104 UTC  BY MSG-3 \n" + "4.  DETECTION FREQUENCY:  406.0369  MHz \n"
				+ "5.  COUNTRY OF BEACON REGISTRATION:  273/ RUSSIA \n"
				+ "6.  USER CLASS: STANDARD LOCATION    PLB - SERIAL NO: 0193 00015  \n"
				+ "7.  EMERGENCY CODE:                      NIL \n" + "8.  POSITIONS: \n"
				+ "          RESOLVED  -                   NIL \n" + "          DOPPLER A -                   NIL \n"
				+ "          DOPPLER B -                   NIL \n" + "          ENCODED   - 55 45.00 N \n"
				+ "                    - 037 45.00 E \n" + "          ENCODED   - UPDATE TIME UNKNOWN \n"
				+ "9.  ENCODED POSITION PROVIDED BY INTERNAL DEVICE \n" + "10. NEXT PASS TIMES (UTC): \n"
				+ "          RESOLVED  -                   NIL \n" + "          DOPPLER A -                   NIL \n"
				+ "          DOPPLER B -                   NIL \n" + "          ENCODED   -                   NIL \n"
				+ "11. 30-HEX MSG: 911730400F37C4BE478E37FFFFFFFF \n" + "   HOMING SIGNAL   121.5 MHZ \n"
				+ "12. ACTIVATION TYPE:                     NIL \n" + "13. BEACON NUMBER ON AIRCRAFT OR VESSEL NO: "
				+ "14. OTHER ENCODED INFORMATION:  \n"
				+ "		ENCODED POSITION UNCERTAINTY    PLUS-MINUS 30 MINUTES OF LATITUDE AND LONGITUDE \n"
				+ "      TYPE APPROVAL CERTIFICATE NO: 0193    BEACON MODEL - FUSE ISDE, RUSSIA: PARM-406M, KS-NAP \n"
				+ "15. OPERATIONAL INFORMATION:       LUT ID: ITGEO BARI, ITALY     NUMBER OF POINTS: 01 \n"
				+ "16. REMARKS:                             NIL \n" + "END OF MESSAGE \n" + "RCC406E.FMT";
		return str;
	}

}
