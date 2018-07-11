package hidreader;

import java.net.MalformedURLException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;

import com.nordicid.nurapi.NurApi;
import com.nordicid.nurapi.NurApiException;
import com.nordicid.nurapi.NurApiListener;
import com.nordicid.nurapi.NurApiSocketTransport;
import com.nordicid.nurapi.NurEventAutotune;
import com.nordicid.nurapi.NurEventClientInfo;
import com.nordicid.nurapi.NurEventDeviceInfo;
import com.nordicid.nurapi.NurEventEpcEnum;
import com.nordicid.nurapi.NurEventFrequencyHop;
import com.nordicid.nurapi.NurEventIOChange;
import com.nordicid.nurapi.NurEventInventory;
import com.nordicid.nurapi.NurEventNxpAlarm;
import com.nordicid.nurapi.NurEventProgrammingProgress;
import com.nordicid.nurapi.NurEventTagTrackingChange;
import com.nordicid.nurapi.NurEventTagTrackingData;
import com.nordicid.nurapi.NurEventTraceTag;
import com.nordicid.nurapi.NurEventTriggeredRead;
import com.nordicid.nurapi.NurRespInventory;
import com.nordicid.nurapi.NurTag;
import com.nordicid.nurapi.NurTagStorage;
import com.nordicid.tdt.*;

import java.io.IOException;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.internal.websocket.Base64;
import org.eclipse.paho.client.mqttv3.internal.websocket.Base64.Base64Encoder;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.channels.FileChannel;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.NameValuePair;
import org.apache.http.client.*;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.*;
import org.apache.http.client.params.*;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.*;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.entity.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class hidreader {
	private static MqttClient mosquClient = null;
	private static MqttMessage  message = new MqttMessage();
	private static String topicctl      = "/hidreader/ctl";
	private static String topicsave     = "/hidreader/savesettings";
	private static String topicev       = "/hidreader/events";
	private static String broker 		= "tcp://127.0.0.1:1883";
	private static String clientId     	= "hidreader";
	private static String lastStatus   	= "N/A";
	
	private static SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
	
	private static NurApi mApi = new NurApi();
	
	static class NotifiedTag {
		public String epc;
		public Date firstSeen;
		public Date lastSeen;
	}
	private static HashMap<String, NotifiedTag> mNotifiedTags = new HashMap<String, NotifiedTag>();
	
	private static void log(String str)
	{
		System.out.println(dateFmt.format(new Date()) + ": " + str);
	}
	
	// Init and connect to mqtt broker
    private static void initMqtt()
    {
		if (mosquClient != null && mosquClient.isConnected())
			return;
	
    	try {
			if (mosquClient == null) {
				mosquClient = new MqttClient(broker, clientId);
				mosquClient.setCallback(mqttCallbacks);
			}
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
			connOpts.setMaxInflight(100);
            	
            log("Connecting to broker: " + broker);
            mosquClient.connect(connOpts);
            log("Connected to broker");
            
            int qos = 0;
			mosquClient.subscribe(topicctl, qos);
			mosquClient.subscribe(topicsave, qos);
        } 
        catch (Exception me) {
            log("Cannot connect MQTT: " + me.getMessage());
        }
    }

	// Publish message to specific topic (catch by e.g. browser client)    
    private static void publishMessage(String content, String topic)
	{
		if (mosquClient == null || !mosquClient.isConnected())
			return;
			
		try {
			message.setPayload(content.getBytes());
			mosquClient.publish(topic, message);
			//log("publishMessage() " + content + "  topic " + topic);
		} 
		catch (Exception me) {
			log("Cannot publish MQTT: " + me.getMessage());
			try {
				mosquClient.disconnect();
			} catch (Exception e) { }
		}
	}
    
    // Publish status event
    private static void publishStatus(String status)
	{
		lastStatus = status;
		log("publishStatus() " + status);
		String statusJson = "{ \"type\": \"status\", \"msg\": \""+status+"\" }";
		publishMessage(statusJson, topicev);
	}
    
    private static void publishEvent(String msg)
	{
		log("publishEvent() " + msg);
		msg = msg.replace("\"", "\\\"");
		String statusJson = "{ \"type\": \"event\", \"msg\": \""+msg+"\" }";
		publishMessage(statusJson, topicev);
	}
    
    /**
     * Unescapes a string that contains standard Java escape sequences.
     * <ul>
     * <li><strong>&#92;b &#92;f &#92;n &#92;r &#92;t &#92;" &#92;'</strong> :
     * BS, FF, NL, CR, TAB, double and single quote.</li>
     * <li><strong>&#92;X &#92;XX &#92;XXX</strong> : Octal character
     * specification (0 - 377, 0x00 - 0xFF).</li>
     * <li><strong>&#92;uXXXX</strong> : Hexadecimal based Unicode character.</li>
     * </ul>
     * 
     * @param st
     *            A string optionally containing standard java escape sequences.
     * @return The translated string.
     */
    private static String unescapeJavaString(String st) {

        StringBuilder sb = new StringBuilder(st.length());

        for (int i = 0; i < st.length(); i++) {
            char ch = st.charAt(i);
            if (ch == '\\') {
                char nextChar = (i == st.length() - 1) ? '\\' : st
                        .charAt(i + 1);
                // Octal escape?
                if (nextChar >= '0' && nextChar <= '7') {
                    String code = "" + nextChar;
                    i++;
                    if ((i < st.length() - 1) && st.charAt(i + 1) >= '0'
                            && st.charAt(i + 1) <= '7') {
                        code += st.charAt(i + 1);
                        i++;
                        if ((i < st.length() - 1) && st.charAt(i + 1) >= '0'
                                && st.charAt(i + 1) <= '7') {
                            code += st.charAt(i + 1);
                            i++;
                        }
                    }
                    sb.append((char) Integer.parseInt(code, 8));
                    continue;
                }
                switch (nextChar) {
                case '\\':
                    ch = '\\';
                    break;
                case 'b':
                    ch = '\b';
                    break;
                case 'f':
                    ch = '\f';
                    break;
                case 'n':
                    ch = '\n';
                    break;
                case 'r':
                    ch = '\r';
                    break;
                case 't':
                    ch = '\t';
                    break;
                case '\"':
                    ch = '\"';
                    break;
                case '\'':
                    ch = '\'';
                    break;
                // Hex Unicode: u????
                case 'u':
                    if (i >= st.length() - 5) {
                        ch = 'u';
                        break;
                    }
                    int code = Integer.parseInt(
                            "" + st.charAt(i + 2) + st.charAt(i + 3)
                                    + st.charAt(i + 4) + st.charAt(i + 5), 16);
                    sb.append(Character.toChars(code));
                    i += 5;
                    continue;
                }
                i++;
            }
            sb.append(ch);
        }
        return sb.toString();
    }
    
    private static void SendToSocket(String str) throws IOException
    {
    	 Socket socket = null;
    	    try {
    	    	socket = new Socket(outputAddress, (int)outputPort);
    	        OutputStream outstream = (OutputStream) socket.getOutputStream(); 
    	        PrintWriter out = new PrintWriter(outstream);
    	        out.print(str);
    	        out.flush();
    	        out.close();
    	        outstream.close();
    	    } catch (UnknownHostException e) {
    	    	publishEvent("SendToSocket failed: " + e.getMessage());
    	    } finally {
    	    	if(socket != null)
    	    		socket.close();
    	    }
    }
    
	private static void SendHTTPPost(String str) throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException, ClientProtocolException, IOException
	{
		SSLContextBuilder builder = new SSLContextBuilder();
		builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
		//allow self-signed certificates by default, feel free to modify to fit your requirements
		SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build(), SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
		CloseableHttpClient httpClient = HttpClients.custom().setSSLSocketFactory(sslsf).build();
		String encoding = Base64.encode(postUser + ":" + postPwd);
		HttpPost httpPost = new HttpPost(outputAddress);
		if(postAuth == true)
			httpPost.addHeader("Authorization", "Basic " + encoding);
		if(postHeader == 0)
		{
			StringEntity params = new StringEntity(str);
			httpPost.setEntity(params);
			httpPost.addHeader("content-type", "application/json");
			log("Posting JSON");
		}
		else
		{
			ArrayList<NameValuePair> postParameters = new ArrayList<NameValuePair>(); 
			postParameters.add(new BasicNameValuePair(postKey, str));
			httpPost.setEntity(new UrlEncodedFormEntity(postParameters, "UTF-8"));
			httpPost.addHeader("content-type", "multipart/form-data");
		}
		CloseableHttpResponse response = httpClient.execute(httpPost);
	}
	
	private static String getReplStr(String replStr, NurTag t)
	{
		if (replStr.startsWith("EPC")) {
			return t.getEpcString(); 
		}
		else if (replStr.startsWith("ANTID")) {
			return Integer.toString(t.getAntennaId());
		}
		else if (replStr.startsWith("RSSI")) {
			return Integer.toString(t.getRssi()); 
		}
		else if (replStr.startsWith("SRSSI")) {
			return Integer.toString(t.getScaledRssi()); 
		}
		else if (replStr.startsWith("FREQ")) {
			return Integer.toString(t.getFreq()); 
		}
		else if (replStr.startsWith("URI")) {
			try
			{
				return new EPCTagEngine(t.getEpcString()).buildTagURI();
			}
			catch(Exception e)
			{
				return t.getEpcString();
			}
		}
		return null;
	}
	
	private static String replaceOne(String str, NurTag t, String replStr, int startPos, int endPos, int []nextPos)
	{
		String repl = getReplStr(replStr, t);
		
		if (repl == null) 
		{
			int curly = replStr.indexOf('{');
			if (curly != -1) {
				nextPos[0] = startPos + curly;
			} else {
				nextPos[0] = endPos;
			}
			return str;		
		}
		
		final Pattern pattern = Pattern.compile(":(.*?)\\Z");
		final Matcher matcher = pattern.matcher(replStr);
		int stripLen = 0;
		if (matcher.find()) {
			try {
				stripLen = Integer.parseInt(matcher.group(1));				
				if (stripLen < 0) {
					repl = repl.substring(repl.length() + stripLen);
				} else {
					repl = repl.substring(0, stripLen);
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
	    }		

		String ret = str.substring(0, startPos);
		ret += repl;
		
		nextPos[0] = ret.length();
		
		ret += str.substring(endPos);		
		
		return ret;
	}
	
	private static String replaceAll(String str, NurTag t)
	{
		Pattern pattern = Pattern.compile("\\{(.+?)\\}");	
		int []nextPos = new int[1];
		nextPos[0] = 0;
		int maxLoops = 1000;
		while (maxLoops-- > 0)
		{			
			Matcher matcher = pattern.matcher(str);
			if (matcher.find(nextPos[0])) {
				str = replaceOne(str, t, matcher.group(1), matcher.start(), matcher.end(), nextPos);				
			} else {
				break;
			}
		}

		return str;
	}

	private static void notifyTag(NurTag t)
	{
		log("notifyTag() " + t.getEpcString() + "; outputType " + outputType);
		try {
			String str = replaceAll(outputFormat, t);
			
//			log("Notify tag: " + str);
			publishEvent("Notify tag: " + str);			
			
			if (outputType == 1) 
			{
				str = unescapeJavaString(str);
				FileWriter file = new FileWriter("/dev/uartRoute");
				file.write(str);
				file.flush();
				file.close();
			}
			else if(outputType == 2)
			{
				SendToSocket(str);
			}
			else if(outputType == 3)
			{
				SendHTTPPost(str);
			}
			
		} 
		catch (Exception e)
		{
			publishEvent("notifyTag failed, stopping inventory. Exception: " + e.toString());
			publishMessage("stop", topicctl);
			e.printStackTrace();
		}
	}
	
	static boolean newTagsAdded = false;
	
	private static void handleNewTags()
	{
		Date now = new Date();		
		newTagsAdded = false;
		synchronized (mApi.getStorage()) 
		{
			for (int n=0; n<mApi.getStorage().size(); n++)
			{
				NurTag t = mApi.getStorage().get(n);				
				if (mNotifiedTags.containsKey(t.getEpcString())) {
					mNotifiedTags.get(t.getEpcString()).lastSeen = now;
				} else {
					newTagsAdded = true;
					NotifiedTag notifiedTag = new NotifiedTag();
					notifiedTag.epc = t.getEpcString();
					notifiedTag.firstSeen = now;
					notifiedTag.lastSeen = now;
					mNotifiedTags.put(t.getEpcString(), notifiedTag);
					notifyTag(t);
				}
			}
			mApi.getStorage().clear();
		}
	}
	
    // Publish inventoried tags
    private static void publishTags()
	{
		newTagsAdded = false;
		houseKeeping();
	
		Date now = new Date();
		JSONObject tagsJson = new JSONObject();
		JSONArray tagsList = new JSONArray();
		Iterator it = mNotifiedTags.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry pair = (Map.Entry)it.next();
			NotifiedTag notifiedTag = (NotifiedTag)pair.getValue();
			JSONObject tagJson = new JSONObject();
			tagJson.put("epc", notifiedTag.epc);
			tagJson.put("firstSeen", notifiedTag.firstSeen.getTime());
			tagJson.put("lastSeen", now.getTime() - notifiedTag.lastSeen.getTime());
			tagsList.add(tagJson);
		}
		tagsJson.put("type", "tags");
		tagsJson.put("tags", tagsList);
		publishMessage(tagsJson.toString(), topicev);		
	}
	
	private static boolean houseKeeping()
	{
		boolean ret = false;
		Date now = new Date();
		Iterator it = mNotifiedTags.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry pair = (Map.Entry)it.next();
			NotifiedTag notifiedTag = (NotifiedTag)pair.getValue();
			if ((now.getTime()-notifiedTag.lastSeen.getTime())/1000 >= notifyUniqueTime) {
				log("Expired tag: " + notifiedTag.epc);
				publishEvent("Expired tag: " + notifiedTag.epc);
				it.remove();
				ret = true;
			}
		}
		return ret;
	}
    
    // Connect nur reader over TCP/IP
    private static boolean connectNurIP(String addr, int port)
    {
		try {
			mApi.setTransport(new NurApiSocketTransport(addr, port));
			mApi.connect();
			return true;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
    }

    static String settingsFile = System.getenv("HOME") + "/../frontend/settings.json";
	static String outputFormat = "{EPC}\\n";
	static long txLevel = 0;
	static long notifyUniqueTime = 3600;
	static long outputType = 0;
	static String outputAddress = "";
	static long outputPort = 80;
	static long postHeader = 0;
	static String postKey = "";
	static String postUser = "";
	static String postPwd = "";
	static Boolean postAuth = false;
	
	static JSONObject getSettingsJsonObject()
	{
		JSONObject obj = new JSONObject();
		obj.put("outputFormat", outputFormat);
	    obj.put("txLevel", txLevel);
	    obj.put("notifyUniqueTime", notifyUniqueTime);
	    obj.put("outputType", outputType);
	    obj.put("outputAddress", outputAddress);
	    obj.put("outputPort", outputPort);
	    obj.put("postHeader", postHeader);
	    obj.put("postKey", postKey);
	    obj.put("postUser", postUser);
	    obj.put("postPwd", postPwd);
	    obj.put("postAuth", postAuth);
	    log("getSettingsJsonObject: " + postUser);
		return obj;
	}

	static void saveSettings()
	{
		try {
			JSONObject obj = getSettingsJsonObject();
			log("saveSettings " +  obj.get("postUser"));
			FileWriter file = new FileWriter(settingsFile);
			file.write(obj.toJSONString());
		    file.flush();

			log("SAVED:");
			log(obj.toString());
	    } catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	static void publishSettings()
	{
		JSONObject obj = new JSONObject();
		obj.put("type", "settings");
		obj.put("settings", getSettingsJsonObject());
		publishMessage(obj.toString(), topicev);
	}

	static void loadSettings(String jsonData)
	{
		try {
			JSONParser parser = new JSONParser();
			Object obj;
			if (jsonData.length() == 0) {
				obj = parser.parse(new FileReader(settingsFile));
			} else {
				obj = parser.parse(jsonData);
			}
 			JSONObject jsonObject = (JSONObject) obj;
			if (jsonObject.containsKey("outputFormat"))
				outputFormat = (String) jsonObject.get("outputFormat");
			if (jsonObject.containsKey("txLevel"))
				txLevel = (Long) jsonObject.get("txLevel");
			if (jsonObject.containsKey("notifyUniqueTime"))
				notifyUniqueTime = (Long) jsonObject.get("notifyUniqueTime");
			if (jsonObject.containsKey("outputType"))
				outputType = (Long) jsonObject.get("outputType");
			if (jsonObject.containsKey("outputAddress"))
				outputAddress = (String) jsonObject.get("outputAddress");
			if (jsonObject.containsKey("outputPort"))
				outputPort = (Long) jsonObject.get("outputPort");
			if(jsonObject.containsKey("postHeader"))
		    	postHeader = (Long) jsonObject.get("postHeader");
			if(jsonObject.containsKey("postKey"))
		    	postKey = (String) jsonObject.get("postKey");
			if(jsonObject.containsKey("postUser"))
		    	postUser = (String) jsonObject.get("postUser");
			if(jsonObject.containsKey("postPwd"))
		    	postPwd = (String) jsonObject.get("postPwd");
			if(jsonObject.containsKey("postAuth"))
		    	postAuth = (Boolean) jsonObject.get("postAuth");
		    
		    log("loadSettings: " + postUser);
			log("LOADED:");
			log(jsonObject.toString());
	    } catch (Exception e) {
            e.printStackTrace();
        }

		try {
			if (mApi.isConnected()) {
				mApi.setSetupTxLevel((int)txLevel);
			}
		} catch (Exception ex)
		{ }
	}
    
	public static void main(String[] args) {
		
		log("hidreader main enter; NurApi v" + mApi.getFileVersion());
		loadSettings("");
		saveSettings();

		// Set listener for NurApi
		mApi.setListener(new NurApiListener() {
			@Override
			public void triggeredReadEvent(NurEventTriggeredRead arg0) {
				// TODO Auto-generated method stub
			}
			
			@Override
			public void traceTagEvent(NurEventTraceTag arg0) {
				// TODO Auto-generated method stub
			}
			
			@Override
			public void programmingProgressEvent(NurEventProgrammingProgress arg0) {
				// TODO Auto-generated method stub
			}
			
			@Override
			public void logEvent(int arg0, String arg1) {
				// TODO Auto-generated method stub
			}
			
			@Override
			public void inventoryStreamEvent(NurEventInventory arg0) {
				// Got some tags
				if(arg0.tagsAdded != 0)
				{
					handleNewTags();
				}
				
				if (arg0.stopped)
				{
					// Restart stream
					//log("Restart inventory stream");
					try {
						mApi.startInventoryStream();
					} catch (Exception e)
					{
						publishStatus("error " + e.getMessage());	
					}
				}
			}
			
			@Override
			public void inventoryExtendedStreamEvent(NurEventInventory arg0) {
				// TODO Auto-generated method stub
			}
			
			@Override
			public void frequencyHopEvent(NurEventFrequencyHop arg0) {
				// TODO Auto-generated method stub
			}
			
			@Override
			public void disconnectedEvent() {
				// TODO Auto-generated method stub
			}
			
			@Override
			public void deviceSearchEvent(NurEventDeviceInfo arg0) {
				// TODO Auto-generated method stub
			}
			
			@Override
			public void debugMessageEvent(String arg0) {
				// TODO Auto-generated method stub
			}
			
			@Override
			public void connectedEvent() {
				try {
	                                mApi.setSetupTxLevel((int)txLevel);
                		} catch (Exception ex)
		                { }
			}
			
			@Override
			public void clientDisconnectedEvent(NurEventClientInfo arg0) {
				// TODO Auto-generated method stub
			}
			
			@Override
			public void clientConnectedEvent(NurEventClientInfo arg0) {
				// TODO Auto-generated method stub
			}
			
			@Override
			public void bootEvent(String arg0) {
				// TODO Auto-generated method stub
			}
			
			@Override
			public void IOChangeEvent(NurEventIOChange arg0) {
				// TODO Auto-generated method stub
			}
	
			@Override
			public void autotuneEvent(NurEventAutotune arg0) {
				// TODO Auto-generated method stub
			}
	
			@Override
			public void epcEnumEvent(NurEventEpcEnum arg0) {
				// TODO Auto-generated method stub
			}
	
			@Override
			public void nxpEasAlarmEvent(NurEventNxpAlarm arg0) {
				// TODO Auto-generated method stub
			}
	
			@Override
			public void tagTrackingChangeEvent(NurEventTagTrackingChange arg0) {
				// TODO Auto-generated method stub
			}
	
			@Override
			public void tagTrackingScanEvent(NurEventTagTrackingData arg0) {
				// TODO Auto-generated method stub
			}
		});
	
		while (true)
		{
			try { 
				Thread.sleep(1000);
			} catch (Exception e)
			{
				break;
			}
			
			// init mqtt for sending the results
			initMqtt();

			if (!mApi.isConnected())
			{				
				if (!connectNurIP("localhost", 4333))		
				{					
					publishStatus("noconn");
					continue;
				}
				else 
				{
					try {
						mApi.startInventoryStream();
						publishStatus("running");
					} catch (Exception e)
					{
						publishStatus("error " + e.getMessage());
					}
					//publishStatus("idle");	
				}
			}
			else
			{
				if (houseKeeping() || newTagsAdded) {					
					publishTags();
				}
			}
		}

		log("hidreader main leave");		
	}
	
	/****************************************************************/
	/* Methods to implement the MqttCallback interface              */
	/****************************************************************/
	
	private static MqttCallback mqttCallbacks = new MqttCallback()
	{
		/**
		 * @see MqttCallback#connectionLost(Throwable)
		 */
		 @Override
		public void connectionLost(Throwable cause) {
			// Called when the connection to the server has been lost.
			// An application may choose to implement reconnection
			// logic at this point. This sample simply exits.
			log("Connection to " + broker + " lost!" + cause);			
		}

		/**
		 * @see MqttCallback#deliveryComplete(IMqttDeliveryToken)
		 */
		 @Override
		public void deliveryComplete(IMqttDeliveryToken token) {
			// Called when a message has been delivered to the
			// server. The token passed in here is the same one
			// that was passed to or returned from the original call to publish.
			// This allows applications to perform asynchronous 
			// delivery without blocking until delivery completes.
			//
			// This sample demonstrates asynchronous deliver and 
			// uses the token.waitForCompletion() call in the main thread which
			// blocks until the delivery has completed. 
			// Additionally the deliveryComplete method will be called if 
			// the callback is set on the client
			// 
			// If the connection to the server breaks before delivery has completed
			// delivery of a message will complete after the client has re-connected.
			// The getPendingTokens method will provide tokens for any messages
			// that are still to be delivered.
		}

		/**
		 * @see MqttCallback#messageArrived(String, MqttMessage)
		 */
		 @Override
		public void messageArrived(String topic, MqttMessage message) throws MqttException {
			// Called when a message arrives from the server that matches any
			// subscription made by the client		
			String msg = new String(message.getPayload());
			log("messageArrived() Topic:\t" + topic + "  Message:\t" + msg);

			if (topic.equals(topicsave)) {
				
				loadSettings(msg);
				saveSettings();
				publishEvent("Settings saved");
				publishSettings();
				return;
			}

			if (msg.equals("getTags")) 
			{
				publishTags();
			}
			else if (msg.equals("getSettings")) 
			{
				try {
					publishSettings();
				} catch(Exception e)
				{
					publishStatus("error " + e.getMessage());
				}	
			}
			else if (msg.equals("resetSettings")) 
			{
				try {
					Files.delete(Paths.get(settingsFile));
				} catch(Exception e)
				{
					
				}
				
				try {
					outputFormat = "{EPC}\\n";
					txLevel = 0;
					notifyUniqueTime = 3600;
					outputType = 0;
					saveSettings();
					publishSettings();
				} catch(Exception e)
				{
					publishStatus("error " + e.getMessage());
				}				
			}
			else if (msg.equals("status")) 
			{
				publishStatus(lastStatus);
			}
			else if (msg.equals("clear")) 
			{
				try {
					mNotifiedTags.clear();
					publishEvent("Cleared");
					publishTags();
				} catch(Exception e)
				{
					publishStatus("error " + e.getMessage());
				}
			}
			else if (msg.equals("stop")) 
			{
				try {
					mApi.stopInventoryStream();
					publishStatus("idle");
				} catch(Exception e)
				{
					publishStatus("error " + e.getMessage());
				}	    	
			}
			else if (msg.equals("start")) 
			{
				try {
					mApi.startInventoryStream();
					publishStatus("running");
				} catch(Exception e)
				{
					publishStatus("error " + e.getMessage());
				}
			}
		}
	};

	/****************************************************************/
	/* End of MqttCallback methods                                  */
	/****************************************************************/
}
