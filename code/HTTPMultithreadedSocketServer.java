/*
 * Name: Tushar Garud
 * UTA Id: 1001420891
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * A chat room server which accepts HTTP requests from clients and
 * sends HTTP responses.  When clients connect, a new thread is
 * started to handle an interactive dialog in which the client
 * sends a request and the server thread sends back a HTTP response.
 */
public class HTTPMultithreadedSocketServer {

	//Initialize the class variables
	private ServerSocket listener;
	private JFrame frame = new JFrame("Chat Room Server");
	private JTextArea messageArea = new JTextArea(8, 60);
	private JScrollPane scrollPane = new JScrollPane(messageArea);
	private List<SocketConnection> clients = new ArrayList<SocketConnection>();

	//Initialize variables for database connection
	private Connection connect = null;
	private Statement statement = null;
	private ResultSet resultSet = null;
 
	//Initialize the new server
	public HTTPMultithreadedSocketServer(int port) {
		// Layout GUI
		messageArea.setEditable(false);
		frame.getContentPane().add(scrollPane, "Center");

		//Create a new server socket
		try {
			listener = new ServerSocket(port);
		} catch(Exception ex) {        	
		}
	}

	//Getter method for the ServerSocket
	public ServerSocket getServerSocket() {
		return listener;
	}

	/**
	 * Application method to run the server runs in an infinite loop
	 * listening on port 9898.  When a connection is requested, it
	 * spawns a new thread to do the servicing and immediately returns
	 * to listening.  The server keeps a unique client number for each
	 * client.
	 */
	public static void main(String[] args) throws Exception {
		System.out.println("The server is running.");

		//Initialize the client count to zero
		int clientNumber = 0;

		//Create a new server object
		HTTPMultithreadedSocketServer server = new HTTPMultithreadedSocketServer(9898);

		//Connect to the database and load messages from history
		server.connectToMySql();

		//Layout the GUI
		server.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		server.frame.pack();
		server.frame.setVisible(true);

		try {
			while (true) {
				//Start accepting connections on the server port. And start a new thread once connected 
				SocketConnection sctemp = server.new SocketConnection(server.getServerSocket().accept(), clientNumber++);
				server.clients.add(sctemp);
				sctemp.start();
			}
		} finally {
			//Close database and socket connections
			server.closeSqlConnection();
			server.getServerSocket().close();
		}
	}

	/**
	 * A separate thread to handle requests from each clients. 
	 */
	private class SocketConnection extends Thread {	
		private Socket socket;
		private int clientNumber;
		private String clientName;
		private BufferedReader in;
		private PrintWriter out;
		private StopWatch stopwatch;

		//Initialize the connection variables
		public SocketConnection(Socket socket, int clientNumber) {
			this.socket = socket;
			this.clientNumber = clientNumber;
			log("New connection with client# " + clientNumber + " at " + socket);
		}        

		//Getter method for the output stream
		public PrintWriter getOutputStream()
		{
			return this.out;
		}

		/**
		 * Services this thread's client by accepting HTTP requests
		 * and sending back HTTP responses.
		 */
		public void run() {
			try {

				//Set the input and output streams
				in = new BufferedReader(
						new InputStreamReader(socket.getInputStream()));
				out = new PrintWriter(socket.getOutputStream(), true);

				// Read HTTP requests from clients and send responses
				while (true) {
					if(in.ready()) {
						//Check if user name is set 
						if(clientName!=null) {							
							String line, request="";
							//Read incoming HTTP request
							while ((line = in.readLine()) != null) {
								if(line.equals(""))
									break;
								request += line + "\n";
							}
							//Read request data
							if(in.ready())
								request += in.readLine();

							//Create a HTTP packet from the String received
							HTTPPacket packet = HTTPPacket.decode(request);

 							//If received request is a POST request
							if(packet.getRequest_type().equals("POST")) {
								
								//Get the time to display by either starting the stopwatch 
								//or by calculating the elapsed time
								String timeSpan;
								if(stopwatch==null) {
									stopwatch = new StopWatch();
									timeSpan="(00:00)";
								} else {
									timeSpan = stopwatch.getElapsedTime();
									stopwatch.reset();
								}

								//Get the type, recipient and content of the message
								String msgType="", recipient="", msgText=""; 
								String input = packet.getData();
								String[] inpFields = input.split("&");
								for(String kvPair: inpFields)
								{
									String[] kv = kvPair.split("=");
									if(kv[0].equals("type"))
										msgType = kv[1];
									else if(kv[0].equals("to"))
										recipient = kv[1];
									else if(kv[0].equals("message"))
										msgText = kv[1];
								}
							
								//If the request is to exit then stop the thread								
								if (msgText == null || msgText.equalsIgnoreCase("exit")) {
									break;
								}

								//Save the message in database if message type is msg								
								if(msgType.equals("msg")) 
								{
									//For multicast message
									if(recipient.equals("everyone")) 
									{
										for(SocketConnection client : clients)
										{
											//Send message to every other process except coordinator and sender process
											if((!client.clientName.equals(clientName)) && (!client.clientName.equals("coordinator")))
												{
													addToDb(clientName, client.clientName, timeSpan, msgText);
													////	Uncomment below line to test to demo timeout by participant while waiting for DECISION 
													////if(msgText.equals("GLOBAL_COMMIT") || msgText.equals("GLOBAL_ABORT")) { break; }
												}											
										}
									}	
									else
									{	//For non-multicast message
										addToDb(clientName, recipient, timeSpan, msgText);
									}
								}
								
								//Display the message
								messageArea.append(clientName + ": \n" + request + "\n\n");
								scrollPane.getVerticalScrollBar().setValue(scrollPane.getVerticalScrollBar().getMaximum());
								
							} else if(packet.getRequest_type().equals("GET")) {
								
								//If the request received is a GET request
								
								//Display the request contents on server
								messageArea.append(clientName + ": \n" + request + "\n\n");
								scrollPane.getVerticalScrollBar().setValue(scrollPane.getVerticalScrollBar().getMaximum());

								//Get all the unread messages for that client from database
								ArrayList<String> msgsAvailable = getMessagesFromDb(clientName);
								
								//If there are undelivered messages then deliver them
								if(!msgsAvailable.isEmpty())
								{																
									//Create a new packet
									HTTPPacket responsePacket = new HTTPPacket();
									responsePacket.setHttp_version("HTTP/1.1");
									responsePacket.setReturn_code("200");
									responsePacket.setStatus("OK");
									responsePacket.setDate_time(new Date());
									responsePacket.setServer("Apache/2.2.14 (Win32)");
									responsePacket.setLast_modified(new Date());
									responsePacket.setContent_type("text/plain");
									responsePacket.setConnection("Closed");

									//Send the undelivered messages
									for(String str: msgsAvailable) {
										responsePacket.setContent_length(str.length());									
										responsePacket.setData(str);
										out.println(responsePacket.toString());
									}									
								}
							}
						}
						else {
							
							//Since the client name is empty,							
							//read the client name from input stream
							clientName = in.readLine();

							//Display message on server
							messageArea.append(clientName + " joined the chat room.\n");
							scrollPane.getVerticalScrollBar().setValue(scrollPane.getVerticalScrollBar().getMaximum());

							//Create a new HTTP response packet
							String response = clientName + " joined the chat room.";
							HTTPPacket responsePacket = new HTTPPacket();
							responsePacket.setHttp_version("HTTP/1.1");
							responsePacket.setReturn_code("200");
							responsePacket.setStatus("OK");
							responsePacket.setDate_time(new Date());
							responsePacket.setServer("Apache/2.2.14 (Win32)");
							responsePacket.setLast_modified(new Date());
							responsePacket.setContent_type("text/plain");
							responsePacket.setConnection("Closed");
							responsePacket.setContent_length(response.length());									
							responsePacket.setData(response);
							String packetStr = responsePacket.toString();				

							//Send joining notification to all users
							for(SocketConnection client : clients){
								client.getOutputStream().println(packetStr);
							}
						}
					}
				}
			} catch (Exception e) {
				log("Error handling client# " + clientNumber + ": " + e);
			} finally {
				try {

					//When a user leaves the room, display notification on server
					messageArea.append(clientName + " left the chat room.\n");
					scrollPane.getVerticalScrollBar().setValue(scrollPane.getVerticalScrollBar().getMaximum());

					//Create a HTTP response packet
					String response = clientName + " left the chat room.";
					HTTPPacket responsePacket = new HTTPPacket();
					responsePacket.setHttp_version("HTTP/1.1");
					responsePacket.setReturn_code("200");
					responsePacket.setStatus("OK");
					responsePacket.setDate_time(new Date());
					responsePacket.setServer("Apache/2.2.14 (Win32)");
					responsePacket.setLast_modified(new Date());
					responsePacket.setContent_type("text/plain");
					responsePacket.setConnection("Closed");
					responsePacket.setContent_length(response.length());									
					responsePacket.setData(response);
					String packetStr = responsePacket.toString();	

					//Send the notification to all users
					for(SocketConnection client : clients){
						client.getOutputStream().println(packetStr);
					}

					//Since user left the room, close the socket
					socket.close();

				} catch (IOException e) {
					log("Couldn't close a socket.");
				} 
			}
		}

		/**
		 * Logs a simple message.  In this case we just write the
		 * message to the server applications standard output.
		 */
		private void log(String message) {
			System.out.println(message);
		}
	}

	
//	Connect to the MySQL database 
	public void connectToMySql()
	{	    
		try {
			// This will load the MySQL driver, each DB has its own driver
			Class.forName("com.mysql.jdbc.Driver");

			// Setup the connection with the DB
			connect = DriverManager.getConnection("jdbc:mysql://localhost/http_socket_db?" + "user=root&password=");

			// Statements allow to issue SQL queries to the database
			statement = connect.createStatement();

		} catch(Exception ex) {
			System.out.println(ex.getMessage());
		} 
	}

	//Insert a new message record in the database
	public void addToDb(String sender, String receiver, String date_time, String content) {
		try {
			//Insert using prepared statement
			PreparedStatement preparedStatement=null;
			preparedStatement = connect.prepareStatement("insert into messages(sender, receiver, msg_time, content) values (?, ?, ?, ?)");
			preparedStatement.setString(1, sender);
			preparedStatement.setString(2, receiver);
			preparedStatement.setString(3, date_time);
			preparedStatement.setString(4, content);
			preparedStatement.executeUpdate();
		} catch(Exception ex) {
			System.out.println(ex.getMessage());
		}
	}

	//Get unread messages for a specific client
	public ArrayList<String> getMessagesFromDb(String clientName) {
		ArrayList<String> response= new ArrayList<String>();
		try {
			// Get the result of the SQL query
			resultSet = statement.executeQuery("select * from messages where receiver=\'" + clientName + "\'");

			//Append every message to the list
			while (resultSet.next()) {
				response.add(resultSet.getString("sender") + " " + resultSet.getString("msg_time") + " : " + resultSet.getString("content"));
			}
			
			//Delete the read messages
			PreparedStatement preparedStatement=null;
			preparedStatement = connect.prepareStatement("delete from messages where receiver=?");
			preparedStatement.setString(1, clientName);
			preparedStatement.executeUpdate();
			
		} catch(Exception ex) {
			System.out.println(ex.getMessage());
		}
		return response;
	}

	//Close the SQL resultset, statement and connection
	public void closeSqlConnection() {
		try {
			if (resultSet != null) {
				resultSet.close();
			}

			if (statement != null) {
				statement.close();
			}

			if (connect != null) {
				connect.close();
			}
		} catch (Exception e) {

		}
	}
}