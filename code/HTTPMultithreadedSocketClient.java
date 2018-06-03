/*
 * Name: Tushar Garud
 * UTA Id: 1001420891
 */

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Date;
import java.util.regex.Pattern;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/**
 * A simple Swing-based client.
 * It has a main frame window with a text field for entering
 * messages and a textarea to see the messages from others.
 */
public class HTTPMultithreadedSocketClient {
	
	//Declare the class variables
	private PrintWriter out;
	private JFrame frame = new JFrame("Client");
	private JTextField dataField = new JTextField(40);
	private JTextArea messageArea = new JTextArea(8, 60);
	private JScrollPane scrollPane = new JScrollPane(messageArea);
	private JButton btnCommit = new JButton("Prepare Commit");
	private JButton btnAbort = new JButton("Abort");
	private JButton readyCommit = new JButton("Acknowledge");
	private Socket socket;
	private BufferedReader in;
	String userName = "";
	String state="IDLE";
	String arbitaryString="";
	Boolean timer_on = false;
	StopWatch stopwatch;
	long TIMEOUT_SECS=30;
	
	//A separate thread to poll the server continuously by sending GET requests
	private class ServerSocketReader extends Thread 
	{                
		public void run() 
		{
			try 
			{
				//Create a GET request packet
				HTTPPacket requestPacket = new HTTPPacket();
				requestPacket.setRequest_type("GET");
				requestPacket.setResource("http://localhost/messages/"+userName);
				requestPacket.setHttp_version("HTTP/1.1");
				requestPacket.setHost("localhost");
				requestPacket.setUser_agent("HTTPTool/1.0");
				requestPacket.setAccept_type("text/plain");
				requestPacket.setAccept_language("en-us");
				
				while(true)
				{
					//Wait for 2000 milliseconds before sending next GET request
					Thread.sleep(2000);
					
					//Update date and messages count
					requestPacket.setDate_time(new Date());										
					
					//Send the GET request to server
					out.println(requestPacket.toString()); 
					out.flush();	        			
					
					//Check it the timer flag is on 
					if(timer_on) {

						//Check if a timeout has occured
						if(stopwatch.getElapsedInSecs() > TIMEOUT_SECS) {

							//If the current state is INIT
							if(state.equals("INIT")) {
								//Turn off the timer
								timer_on = false;
								//Write VOTE_ABORT to log file								
								writeToFile("VOTE_ABORT");
								//Change state to ABORT
								state = "ABORT";
								
								//Write VOTE_ABORT to display
								messageArea.append(userName + " " + stopwatch.getElapsedTime() + " : VOTE_ABORT\n");
								scrollPane.getVerticalScrollBar().setValue(scrollPane.getVerticalScrollBar().getMaximum());
							}
							
							//If current state is READY
							if(state.equals("READY")) {
								//Turn off the timer
								timer_on = false;	
								
								//Multicast DECISION_REQUEST to other participants using HTTP POST request
								HTTPPacket packet = new HTTPPacket();
								packet.setRequest_type("POST");
								packet.setResource("/");
								packet.setHttp_version("HTTP/1.1");
								packet.setHost("localhost");
								packet.setUser_agent("HTTPTool/1.0");
								packet.setContent_type("text/plain");
								packet.setDate_time(new Date());								
								String msgData = "type=msg&to=everyone&message=DECISION_REQUEST";				
								packet.setContent_length(msgData.length());
								packet.setData(msgData);
								
								//Send the request to the server
								out.println(packet.toString());
								out.flush();								
							}
						}
						
					}

					//Check for input
					if(in.ready()) {						
						String line, request="";
						
						//Read response from server
						while ((line = in.readLine()) != null) {
							if(line.equals(""))
								break;
							request += line + "\n";
						}
						
						//Read response data
						if(in.ready())
							request += in.readLine();
						
						//Create a packet object from the string received
						HTTPPacket packet = HTTPPacket.decode(request);
						
						//Check contents inside the the packet
						String input = packet.getData();						
						if (input == null)
							break;
						
						//If message has the arbitary string
						if(input.contains("STRING:"))
						{
							//Get the string from message
							arbitaryString = input.split(":")[3];

							//Chage state to INIT
							state="INIT";
							System.out.println(userName+":INIT");

							//Write INIT to log file
							writeToFile("INIT");
							
							//Start the stopwatch and timer
							stopwatch = new StopWatch();
							timer_on = true;
						}
						
						//If the message is a vote request
						else if(input.contains("REQUEST_VOTE"))
						{
							//Turn off the timer
							timer_on = false;
							
							//Enable commit and abort buttons
							btnCommit.setEnabled(true);
							btnAbort.setEnabled(true);
						}
						
						//If the message is a GLOBAL_ABORT message and current state is READY or INIT
						else if(input.contains("GLOBAL_ABORT") && (state.equals("READY") || state.equals("INIT"))) 
						{
							//Turn off the timer
							timer_on=false;
							
							//Change curent state to ABORT
							state="ABORT";
							
							//Write GLOBAL_ABORT to output file
							writeToFile("GLOBAL_ABORT");
							System.out.println(userName+":ABORT");
							
							//Disable the Commit and Abort buttons
							btnCommit.setEnabled(false);
							btnAbort.setEnabled(false);
						}
						
						//If the message is a PREPARE_COMMIT message and current state is READY
						else if(input.contains("PREPARE_COMMIT") && state.equals("READY")) 
						{
							//Turn off the timer
							timer_on=false;							
							
							//Disable the Commit and Abort buttons
							btnCommit.setEnabled(false);
							btnAbort.setEnabled(false);
							
							//Enable button to send READY_COMMIT to coordinator
							readyCommit.setEnabled(true);							
						}

						//If the message is a GLOBAL_COMMIT message and curent state is READY
						else if(input.contains("GLOBAL_COMMIT") && state.equals("PRECOMMIT")) 
						{
							//Turn off the timer
							timer_on=false;
							
							//Change state to COMMIT
							state="COMMIT";
							
							//Write the arbitary string and GLOBAL_COMMIT to log file
							writeToFile(arbitaryString);
							writeToFile("GLOBAL_COMMIT");
							System.out.println(userName+":COMMIT");
							
						}
						
						/*		//If the message is a GLOBAL_COMMIT message and curent state is READY
						else if(input.contains("GLOBAL_COMMIT") && state.equals("READY")) 
						{
							//Turn off the timer
							timer_on=false;
							
							//Change state to COMMIT
							state="COMMIT";
							
							//Write the arbitary string and GLOBAL_COMMIT to log file
							writeToFile(arbitaryString);
							writeToFile("GLOBAL_COMMIT");
							System.out.println(userName+":COMMIT");
							
							//Disable the Commit and Abort buttons
							btnCommit.setEnabled(false);
							btnAbort.setEnabled(false);
						}		*/
						
						//If the input is a decision request
						else if(input.contains("DECISION_REQUEST"))
						{
							//Get the sender name
							String sender = input.split("[(]")[0].trim(); // ( is a regex metachracter thats why the [] is used
							
							//Get the last line from log file
							String status = getLastLineFromLog();
							
							//If the last line is GLOBAL_COMMIT
							if(status.equals("GLOBAL_COMMIT")) {

								//send global commit to sender 
								HTTPPacket gc_packet = new HTTPPacket();
								gc_packet.setRequest_type("POST");
								gc_packet.setResource("/");
								gc_packet.setHttp_version("HTTP/1.1");
								gc_packet.setHost("localhost");
								gc_packet.setUser_agent("HTTPTool/1.0");
								gc_packet.setContent_type("text/plain");
								gc_packet.setDate_time(new Date());								
								String msgData = "type=msg&to=" + sender + "&message=GLOBAL_COMMIT";				
								gc_packet.setContent_length(msgData.length());
								gc_packet.setData(msgData);
								
								//Send the POST request to the server
								out.println(gc_packet.toString());
								out.flush();
							}
							
							//If the last line is INIT, GLOBAL_ABORT or VOTE_ABORT
							else if(status.equals("INIT") || status.equals("GLOBAL_ABORT") || status.equals("VOTE_ABORT")) {

								//send global abort to sender
								HTTPPacket ga_packet = new HTTPPacket();
								ga_packet.setRequest_type("POST");
								ga_packet.setResource("/");
								ga_packet.setHttp_version("HTTP/1.1");
								ga_packet.setHost("localhost");
								ga_packet.setUser_agent("HTTPTool/1.0");
								ga_packet.setContent_type("text/plain");
								ga_packet.setDate_time(new Date());								
								String msgData = "type=msg&to=" + sender + "&message=GLOBAL_ABORT";				
								ga_packet.setContent_length(msgData.length());
								ga_packet.setData(msgData);
							
								//Send the request to the server
								out.println(ga_packet.toString());
								out.flush();
							}
						}
						
						//If it contains a message, display the message
						messageArea.append(input + "\n");
						//Scroll down the window
						scrollPane.getVerticalScrollBar().setValue(scrollPane.getVerticalScrollBar().getMaximum());
					}
				}
			}
			catch(Exception ex) 
			{
			}
		}
	}
	
	/**
	 * Constructs the client by laying out the GUI and registering a
	 * listener with the textfield so that pressing Enter in the
	 * listener sends a POST request to the server.
	 */
	public HTTPMultithreadedSocketClient() {

		// Layout GUI
		messageArea.setEditable(false);
		frame.getContentPane().add(dataField, "North");
		frame.getContentPane().add(btnCommit, "East");
		frame.getContentPane().add(btnAbort, "West");
		frame.getContentPane().add(readyCommit, "South");
		frame.getContentPane().add(scrollPane, "Center");     
		btnCommit.setEnabled(false);
		btnAbort.setEnabled(false);
		readyCommit.setEnabled(false);


		// Add Listener for the text field
		dataField.addActionListener(new ActionListener() {			
			/**
			 * Responds to pressing the enter key in the textfield
			 * by sending the contents of the text field to the server
			 * in a POST request.
			 */
			public void actionPerformed(ActionEvent e) {
				
				//Create a HTTP POST request packet
				HTTPPacket packet = new HTTPPacket();
				packet.setRequest_type("POST");
				packet.setResource("/");
				packet.setHttp_version("HTTP/1.1");
				packet.setHost("localhost");
				packet.setUser_agent("HTTPTool/1.0");
				packet.setContent_type("text/plain");
				packet.setDate_time(new Date());
				
				//If the message is to exit the connection, then its an info message to the server 
				//otherwise its a msg message to the coordinator
				String msgData;
				if(dataField.getText().equalsIgnoreCase("exit"))
					msgData = "type=info&to=server&message=exit";
				else
					msgData = "type=msg&to=coordinator&message=" +	dataField.getText();				
				packet.setContent_length(msgData.length());
				packet.setData(msgData);

				//Send the request to the server
				out.println(packet.toString());
				out.flush();

				//If user entered 'Exit', then exit the connection
				if(dataField.getText().equalsIgnoreCase("exit")) {
					try {
						frame.dispose();
						in.close();
						out.close();
					} catch(IOException ex) {}
				}
				else {
					//Reset the textbox to blank
					dataField.setText("");
				}            	
			}
		});
		
		// Event listener for Commit button
		btnCommit.addActionListener(new ActionListener() {			
			
			public void actionPerformed(ActionEvent e) {
			
				//Disable the Commit and Abort buttons
				btnCommit.setEnabled(false);
				btnAbort.setEnabled(false);
				
				//Write vote to log file
				writeToFile("VOTE_COMMIT");
				
				//Create a HTTP POST request packet to send the vote to coordinator
				HTTPPacket packet = new HTTPPacket();
				packet.setRequest_type("POST");
				packet.setResource("/");
				packet.setHttp_version("HTTP/1.1");
				packet.setHost("localhost");
				packet.setUser_agent("HTTPTool/1.0");
				packet.setContent_type("text/plain");
				packet.setDate_time(new Date());
				
				String msgData = "type=msg&to=coordinator&message=VOTE_COMMIT";				
				packet.setContent_length(msgData.length());
				packet.setData(msgData);

				//Send the request to the server
				out.println(packet.toString());
				out.flush();
				
				//Change state to READY
				state="READY";
				
				//Start the timer and stopwatch
				timer_on = true;
				stopwatch = new StopWatch();
				System.out.println(userName+":READY");
			}
		});
		
		// Event listener for Abort button
		btnAbort.addActionListener(new ActionListener() {			

			public void actionPerformed(ActionEvent e) {				

				//Disable Commit and Abort buttons
				btnCommit.setEnabled(false);
				btnAbort.setEnabled(false);
				
				//Write vote to file
				writeToFile("VOTE_ABORT");
				
				//Create a HTTP POST request packet to send the vote to coordinator
				HTTPPacket packet = new HTTPPacket();
				packet.setRequest_type("POST");
				packet.setResource("/");
				packet.setHttp_version("HTTP/1.1");
				packet.setHost("localhost");
				packet.setUser_agent("HTTPTool/1.0");
				packet.setContent_type("text/plain");
				packet.setDate_time(new Date());
				
				String msgData = "type=msg&to=coordinator&message=VOTE_ABORT";				
				packet.setContent_length(msgData.length());
				packet.setData(msgData);

				//Send the request to the server
				out.println(packet.toString());
				out.flush();
				
				//Change current state to ABORT
				state="ABORT";
				System.out.println(userName+":ABORT");
			}
		});
		
		//Event listener for ready-commit button
		readyCommit.addActionListener(new ActionListener() {			
			
			public void actionPerformed(ActionEvent e) {
			
				//Disable the readyCommit button
				readyCommit.setEnabled(false);
				
				//Create a HTTP POST request packet to send the ack to coordinator
				HTTPPacket packet = new HTTPPacket();
				packet.setRequest_type("POST");
				packet.setResource("/");
				packet.setHttp_version("HTTP/1.1");
				packet.setHost("localhost");
				packet.setUser_agent("HTTPTool/1.0");
				packet.setContent_type("text/plain");
				packet.setDate_time(new Date());
				
				String msgData = "type=msg&to=coordinator&message=READY_COMMIT";				
				packet.setContent_length(msgData.length());
				packet.setData(msgData);

				//Send the request to the server
				out.println(packet.toString());
				out.flush();
				
				//Change state to READY
				state="PRECOMMIT";
				
				//Start the timer and stopwatch
				//timer_on = true;
				//stopwatch = new StopWatch();
				writeToFile("PRECOMMIT");
				System.out.println(userName+":PRECOMMIT");
			}
		});
				
	}

	/**
	 * Implements the connection logic by setting 
	 * the server's IP address, connecting and setting up streams
	 */
	public void connectToServer() throws IOException {

		// Get the user name from a dialog box.
		while(userName.equals("")) {
			userName = JOptionPane.showInputDialog(
					frame,
					"Enter user name:",
					"Welcome to the Capitalization Program",
					JOptionPane.QUESTION_MESSAGE);

			if(!Pattern.compile("[a-zA-Z]+").matcher(userName).matches()) {
				userName="";
				JOptionPane.showMessageDialog(null, "Please enter a valid name containing only letters.");
			}
		}    	

		//Set frame title
		frame.setTitle(userName);

		//Set the server IP address as current computer
		String serverAddress = "localhost";

		// Make connection and initialize streams
		socket = new Socket(serverAddress, 9898);
		in = new BufferedReader(
				new InputStreamReader(socket.getInputStream()));
		out = new PrintWriter(socket.getOutputStream(), true);

		out.println(userName);

		//Start the reader thread to read messages from server
		new ServerSocketReader().start();
	}

	/**
	 * Create a client process, display it and connect to the server
	 */
	public static void main(String[] args) throws Exception {

		//Create an instance of HTTPMultithreadedSocketClient class
		HTTPMultithreadedSocketClient client = new HTTPMultithreadedSocketClient();
		
		//Display the user interface
		client.frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		client.frame.pack();
		client.frame.setVisible(true);
		
		//Connect to the socket server
		client.connectToServer();
		
		//Display the old contents on log file on user interface
		client.displayOldData();
	}
	
	//Appends a line to the log file named <user name>.txt
	public void writeToFile(String line)
	{
		try 
		{
			//Initialize the BufferedWriter
			FileWriter fileWriter = new FileWriter(userName+".txt", true);
			BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
            bufferedWriter.newLine();
            
            //Append line to log file
			bufferedWriter.write(line);
			
			//Close the BufferedWriter
            bufferedWriter.close();
		}
		catch(IOException ex) {
			ex.printStackTrace();
		}
	}
	
	//Returns the last string from log file
	public String getLastLineFromLog()
	{
		String line, result="";
		
		try 
		{
			//Initialize the BufferedReader
			FileReader fileReader = new FileReader(userName+".txt");
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            
            //Read file line by line
            while((line = bufferedReader.readLine()) != null) {
                result = line;
            }   
            
            //Close the BufferedReader
            bufferedReader.close();           
		}
		catch(IOException ex) {
			ex.printStackTrace();
		}
		
		//Return the last line
        return result;
	}
	
	//Displays the contents of log file on user interface
	public void displayOldData()
	{
		try 
		{
			String line="";
			
			//Check if the log file exists
			File logFile = new File(userName+".txt");
			if(logFile.exists())
			{
				//Initialize the BufferedReader
				FileReader fileReader = new FileReader(logFile);
	            BufferedReader bufferedReader = new BufferedReader(fileReader);
	            
	            //Read the file line by line
	            while((line = bufferedReader.readLine()) != null) 
	            {
	            	//Display the line on user interface
					messageArea.append(line + "\n");
	            }   
	            
	            //Scroll down the page
	            scrollPane.getVerticalScrollBar().setValue(scrollPane.getVerticalScrollBar().getMaximum());
	            
	            //Close the BufferedReader
	            bufferedReader.close();
			}
		}
		catch(IOException ex) {
			ex.printStackTrace();
		}	
	}
}