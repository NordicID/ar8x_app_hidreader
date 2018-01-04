# ar8x_app_hidreader
HID Reader for the Nordic ID AR8x-series & Sampo S2 devices

This application enables your Nordic ID Smart device to output inventory results:
1) to the mini-USB port of the device (when it's in HID or Unmanaged mode)
2) via TCP to a specified ip-address & port
3) via HTTP Post to a separate server/service(application/json or multipart/form-data)

The following options are configurable(either through the web user interface or by modifying the settings.json found in the hidreader*.zip found in this repositorys releases):
Output format(default = "EPC\n"):
  Output format accepts any string content, and can be used together with the following predefined strings which will be replaced by the application before outputting them; 
    {EPC} = epc, {ANTID} = antenna id, {RSSI} = rssi, {SRSSI} = scaled rssi, {FREQ} = frequency, {URI} = the EPC Tag URI , \r = CR, \n = LF, \t = TAB.
  Note that when using HTTP Post & application/json your format should include quotation marks for strings i.e. "{EPC}" or {"code":"{EPC}"}

Notify unique time(default 3600):
  Tags are notified only when not seen in this time (seconds)
  
Nur Tx Level(default 0/Full):
  Rfid reader TX power attenuation

Output type(default None):
  Output method to be used, options:
      None
      HID (output to /dev/uartRoute)
      TCP (output to ip-address:port)
      HTTP Post (output to http-address)

HTTP Post header:
  When Output type is set to HTTP Post, this setting defines what is the post header used. Options are:
      application/json
      multipart/form-data
      
Key name:
  If multipart/form-data is used, this defines the key for the Output value i.e. keyname={EPC} and so on

Basic access authentication used for HTTP
  true/false: indicates whether to use HTTP basic authentication
  
Basic authentication user:
  When HTTP post used and authentication is enabled, this field should contain the username
Basic authentication password
  When HTTP post used and authentication is enabled, this field should contain the password
  
Address:
  IP-address or full path for HTTP post i.e. 127.0.0.1 or http://127.0.0.1/post/tags/here
  
TCP port
  TCP port to use when output type == TCP.
  
Usage notes:
The nginx web server on the Nordic ID smart devices uses self-signed certificate, so most browsers will not accept them by default. This means that for this sample application to work you need to add an exception to the certificate by opening the https://ipaddress-of-your-device:1884 (and following your browsers instructions) so that the websocket of the web user interface is allowed to access the MQTT broker. This applies also to your own applications which either connect to the broker or need to access the platform otherwise, they need to handle the non-CA certificate.

Keep in mind also that you need to first start the application on the web UI of your Smart Device before opening the configuration view for the app. Otherwise the application will not be able to access the settings since there's no "backend" available through the MQTT broker.

If you are not yet familiar with the documentation for the Nordic ID Smart Devices, it's highly recommended that you go through them first before starting to modify and/or use this sample application: https://github.com/NordicID/ar8x_samples/tree/master/docs
