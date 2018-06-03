/*
 * Name: Tushar Garud
 * UTA Id: 1001420891
 */

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/*
 * This class represents a HTTP request or response packet.
 * It has different header fields as shown below
 */
public class HTTPPacket {

	private String request_type;
	private String resource;
	private String http_version;
	private String host;
	private String user_agent;
	private String content_type;
	private int content_length=-1;
	private String data;
	private Date date_time;
	private String accept_type;
	private String accept_language;
	private String server;
	private Date last_modified;
	private String connection;
	private String return_code;
	private String status;
	
	//Getter and setter methods for header fields
	
	public String getReturn_code() {
		return return_code;
	}

	public void setReturn_code(String return_code) {
		this.return_code = return_code;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getServer() {
		return server;
	}

	public void setServer(String server) {
		this.server = server;
	}

	public Date getLast_modified() {
		return last_modified;
	}

	public void setLast_modified(Date last_modified) {
		this.last_modified = last_modified;
	}

	public String getConnection() {
		return connection;
	}

	public void setConnection(String connection) {
		this.connection = connection;
	}

	public String getAccept_type() {
		return accept_type;
	}
	public void setAccept_type(String accept_type) {
		this.accept_type = accept_type;
	}
	public String getAccept_language() {
		return accept_language;
	}
	public void setAccept_language(String accept_language) {
		this.accept_language = accept_language;
	}

	public String getRequest_type() {
		return request_type;
	}
	public void setRequest_type(String request_type) {
		this.request_type = request_type;
	}
	public String getResource() {
		return resource;
	}
	public void setResource(String resource) {
		this.resource = resource;
	}
	public String getHttp_version() {
		return http_version;
	}
	public void setHttp_version(String http_version) {
		this.http_version = http_version;
	}
	public String getHost() {
		return host;
	}
	public void setHost(String host) {
		this.host = host;
	}
	public String getUser_agent() {
		return user_agent;
	}
	public void setUser_agent(String user_agent) {
		this.user_agent = user_agent;
	}
	public String getContent_type() {
		return content_type;
	}
	public void setContent_type(String content_type) {
		this.content_type = content_type;
	}
	public int getContent_length() {
		return content_length;
	}
	public void setContent_length(int content_length) {
		this.content_length = content_length;
	}
	public String getData() {
		return data;
	}
	public void setData(String data) {
		this.data = data;
	}
	public Date getDate_time() {
		return date_time;
	}
	public void setDate_time(Date date_time) {
		this.date_time = date_time;
	}

	//Convert a HTTPPacket object to string
	public String toString()
	{
		String result = "";
		
		//Set date format
		SimpleDateFormat dtFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");

		//To check if its a request or response packet
		if(request_type!=null)
		{
			
			//Its a request packet, so use corresponding header fields
			result += request_type + " " + resource + " " + http_version + "\r\n";
			if(host!=null)
				result += "Host: " + host + "\r\n";
			if(user_agent!=null)
				result += "User-Agent: " + user_agent + "\r\n";		
			if(accept_type!=null)
				result += "Accept: " + accept_type + "\r\n";
			if(accept_language!=null)
				result += "Accept-Language: " + accept_language + "\r\n";				
			if(content_type!=null)
				result += "Content-Type: " + content_type + "\r\n";
			if(content_length > 0)
				result += "Content-Length: " + String.valueOf(content_length) + "\r\n";			
			if(date_time!=null)
				result += "Date: " + dtFormat.format(date_time) + "\r\n";			
			result += "\r\n";
			if(data!=null)
				result += data;
			
		} else {
			
			//Its a response packet, so use corresponding header fields
			result += http_version + " " + return_code + " " + status + "\r\n";
			if(date_time!=null)
				result += "Date: " + dtFormat.format(date_time) + "\r\n";
			if(server!=null)
				result += "Server: " + server + "\r\n";
			if(last_modified!=null)
				result += "Last-Modified: " + dtFormat.format(last_modified) + "\r\n";
			if(content_length > 0)
				result += "Content-Length: " + String.valueOf(content_length) + "\r\n";	
			if(content_type!=null)
				result += "Content-Type: " + content_type + "\r\n";			
			if(connection!=null)
				result += "Connection: " + connection + "\r\n";			
			result += "\r\n";
			if(data!=null)
				result += data;
		}
		
		//Return the generated String
		return result;		
	}

	//Convert a String object to HTTPPacket object
	public static HTTPPacket decode(String request)
	{		
		HTTPPacket packet = new HTTPPacket();

		//Split the String to read line by line
		String[] lines = request.split("\\r?\\n");

		//Check if its a request or response packet
		String[] temp = lines[0].split(" ");
		if(temp.length==3)
		{			
			if(temp[0].startsWith("HTTP")) {
				//Its a response packet
				packet.setHttp_version(temp[0]);
				packet.setReturn_code(temp[1]);
				packet.setStatus(temp[2]);
			} else {
				//Its a request packet
				packet.setRequest_type(temp[0]);
				packet.setResource(temp[1]);
				packet.setHttp_version(temp[2]);
			}
		}

		for(int i=1; i<lines.length; i++)
		{
			//Split the string in name and value pairs
			String[] info = lines[i].split(":",2);
			if(info.length==2)
			{
				//Check each name and assign value to corresponding field
				switch(info[0].trim())
				{
				case "Host":
					packet.setHost(info[1]);
					break;
				case "User-Agent":
					packet.setUser_agent(info[1]);
					break;
				case "Content-Type":
					packet.setContent_type(info[1]);
					break;
				case "Content-Length":
					packet.setContent_length(Integer.parseInt(info[1].trim()));
					break;
				case "Date":
					try {
						packet.setDate_time((new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z")).parse(info[1].trim()));
					}catch(ParseException ex) {
						packet.setDate_time(null);
					}
					break;				
				case "Accept":
					packet.setAccept_type(info[1]);
					break;
				case "Accept-Language":
					packet.setAccept_language(info[1]);
					break;
				case "Server":
					packet.setServer(info[1]);
					break;
				case "Last-Modified":
					try {
						packet.setLast_modified((new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z")).parse(info[1].trim()));
					}catch(ParseException ex) {
						packet.setLast_modified(null);
					}
					break;								
				case "Connection":
					packet.setConnection(info[1]);
					break;						
				}
			}
		}

		//If the packet has some content after header fields
		if(packet.content_length > 0)
			packet.setData(lines[lines.length-1]);

		return packet;
	}

}
