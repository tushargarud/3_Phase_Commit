/*
 * Name: Tushar Garud
 * UTA Id: 1001420891
 */
 
import java.awt.Dimension;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/**
 * A simple Swing-based client for the chat room server.
 * It has a main frame window with a text field for entering
 * messages and a textarea to see the messages from others.
 */
public class HTTPMultithreadedSocketCoordinator {
	
	//Declare class variables
	private PrintWriter out;
	private JFrame frame = new JFrame("Client");
	private JTextField dataField = new JTextField(40);
	private JTextArea messageArea = new JTextArea(8, 60);	
	private JScrollPane scrollPane = new JScrollPane(messageArea);
    private JButton btnStartTras = new JButton("Start Transaction");
	private Socket socket;
	private BufferedReader in;
	String userName = "coordinator";
	String arbitaryString = "";
	String state="IDLE";
	List<String> receivedVotes;
	List<String> readyCommitVotes;
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

					//Don't rearrange the next two blocks. Otherwise the code will stop working
					//If coordinator is in wait state
					if(state.equals("WAIT")) 
					{
						//Check for timeout
						if(stopwatch.getElapsedInSecs() > TIMEOUT_SECS)
						{
							//Write GLOBAL_ABORT to log file
							writeToFile("GLOBAL_ABORT");
							
							//Send GLOBAL ABORT to all participants by HTTP POST request
							HTTPPacket packetCommit = new HTTPPacket();
							packetCommit.setRequest_type("POST");
							packetCommit.setResource("/");
							packetCommit.setHttp_version("HTTP/1.1");
							packetCommit.setHost("localhost");
							packetCommit.setUser_agent("HTTPTool/1.0");
							packetCommit.setContent_type("text/plain");
							packetCommit.setDate_time(new Date());
							String msgData = "type=msg&to=everyone&message=GLOBAL_ABORT";				
							packetCommit.setContent_length(msgData.length());
							packetCommit.setData(msgData);
							out.println(packetCommit.toString());
							out.flush();
							
							//Change the state to abort and enable the Start Transaction button
							state="ABORT";
							System.out.println("Coordinator:ABORT");
							btnStartTras.setEnabled(true);
						}
					}
					
					//If coordinator is in precommit state
					if(state.equals("PRECOMMIT")) 
					{
						//Check for timeout
						if(stopwatch.getElapsedInSecs() > TIMEOUT_SECS)
						{
							
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
						
						//If coordinator is in WAIT state
						if(state.equals("WAIT"))
						{
							//If the client vote for ABORT
							if(input.contains("VOTE_ABORT"))
							{
								//Write GLOBAL_ABORT in log file
								writeToFile("GLOBAL_ABORT");
								
								//Send GLOBAL ABORT to all participants by HTTP POST request
								HTTPPacket packetCommit = new HTTPPacket();
								packetCommit.setRequest_type("POST");
								packetCommit.setResource("/");
								packetCommit.setHttp_version("HTTP/1.1");
								packetCommit.setHost("localhost");
								packetCommit.setUser_agent("HTTPTool/1.0");
								packetCommit.setContent_type("text/plain");
								packetCommit.setDate_time(new Date());
								String msgData = "type=msg&to=everyone&message=GLOBAL_ABORT";				
								packetCommit.setContent_length(msgData.length());
								packetCommit.setData(msgData);
								out.println(packetCommit.toString());
								out.flush();
							
								//Change the state to abort and enable the Start Transaction button
								state="ABORT";
								System.out.println("Coordinator:ABORT");
								btnStartTras.setEnabled(true);
							}
							//If client voted for commit
							else if(input.contains("VOTE_COMMIT"))
							{
								//Record the vote
								receivedVotes.add("VOTE_COMMIT");
								
								//If all clients voted for commit
								if(receivedVotes.size()==3)
								{
									//Write PRECOMMIT to file
									writeToFile("PRECOMMIT");

									//Send PREPARE COMMIT to all participants by HTTP POST request
									HTTPPacket packetAbort = new HTTPPacket();
									packetAbort.setRequest_type("POST");
									packetAbort.setResource("/");
									packetAbort.setHttp_version("HTTP/1.1");
									packetAbort.setHost("localhost");
									packetAbort.setUser_agent("HTTPTool/1.0");
									packetAbort.setContent_type("text/plain");
									packetAbort.setDate_time(new Date());
									String msgData = "type=msg&to=everyone&message=PREPARE_COMMIT";				
									packetAbort.setContent_length(msgData.length());
									packetAbort.setData(msgData);
									out.println(packetAbort.toString());
									out.flush();
									
									//Change the state to commit and enable the Start Transaction button									
									state="PRECOMMIT";
									System.out.println("Coordinator:PRECOMMIT");
									
									readyCommitVotes = new ArrayList<String>();
									stopwatch = new StopWatch();
									
							/*		//Write GLOBAL_COMMIT to file
									writeToFile("GLOBAL_COMMIT");

									//Send GLOBAL COMMIT to all participants by HTTP POST request
									HTTPPacket packetAbort = new HTTPPacket();
									packetAbort.setRequest_type("POST");
									packetAbort.setResource("/");
									packetAbort.setHttp_version("HTTP/1.1");
									packetAbort.setHost("localhost");
									packetAbort.setUser_agent("HTTPTool/1.0");
									packetAbort.setContent_type("text/plain");
									packetAbort.setDate_time(new Date());
									String msgData = "type=msg&to=everyone&message=GLOBAL_COMMIT";				
									packetAbort.setContent_length(msgData.length());
									packetAbort.setData(msgData);
									out.println(packetAbort.toString());
									out.flush();
									
									//Change the state to commit and enable the Start Transaction button									
									state="COMMIT";
									System.out.println("Coordinator:COMMIT");
									btnStartTras.setEnabled(true);		*/
								}
							}
						}
						
						if(state.equals("PRECOMMIT") && input.contains("READY_COMMIT"))
						{
							//Record the vote
							readyCommitVotes.add("READY_COMMIT");
							
							//If all clients voted for commit
							if(readyCommitVotes.size()==3)
							{
							
								//Write GLOBAL_COMMIT to file
								writeToFile("GLOBAL_COMMIT");

								//Send GLOBAL COMMIT to all participants by HTTP POST request
								HTTPPacket packetAbort = new HTTPPacket();
								packetAbort.setRequest_type("POST");
								packetAbort.setResource("/");
								packetAbort.setHttp_version("HTTP/1.1");
								packetAbort.setHost("localhost");
								packetAbort.setUser_agent("HTTPTool/1.0");
								packetAbort.setContent_type("text/plain");
								packetAbort.setDate_time(new Date());
								String msgData = "type=msg&to=everyone&message=GLOBAL_COMMIT";				
								packetAbort.setContent_length(msgData.length());
								packetAbort.setData(msgData);
								out.println(packetAbort.toString());
								out.flush();
								
								//Change the state to commit and enable the Start Transaction button									
								state="COMMIT";
								System.out.println("Coordinator:COMMIT");
								btnStartTras.setEnabled(true);
							}
						}

						//If it contains a message, display the message and scroll down the window
						messageArea.append(input + "\n");
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
	public HTTPMultithreadedSocketCoordinator() {

		// Layout GUI
		messageArea.setEditable(false);
		frame.getContentPane().add(dataField, "North");
		frame.getContentPane().add(btnStartTras, "East");
		frame.getContentPane().add(scrollPane, "Center");        

		// Add Listeners
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
				
				//If the message is to exit the connection, the recipient is server otherwise recipient is coordinator
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

				//If user entered 'Exit', then leave the chat room
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
		
		
		// Listener for Start Transaction button
		btnStartTras.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				
				//Disable the Start Transaction button until current transaction completes
				btnStartTras.setEnabled(false);
				
				//Set current state to INIT 
				state="INIT";
				System.out.println("Coordinator:INIT");
				
				//Write START_3PC to log file to mark the start of transaction
				writeToFile("START_3PC");
				
				arbitaryString="";
				// Get the arbitary string from a dialog box.
				while(arbitaryString.equals("")) {
					arbitaryString = JOptionPane.showInputDialog(
							frame,
							"Enter arbitary string:",
							"Welcome",
							JOptionPane.QUESTION_MESSAGE);

					if(arbitaryString.length()==0) {
						arbitaryString="";
						JOptionPane.showMessageDialog(null, "Please enter a string.");
					}
				}
				
				//Create a HTTP POST request packet
				HTTPPacket packet = new HTTPPacket();
				packet.setRequest_type("POST");
				packet.setResource("/");
				packet.setHttp_version("HTTP/1.1");
				packet.setHost("localhost");
				packet.setUser_agent("HTTPTool/1.0");
				packet.setContent_type("text/plain");
				packet.setDate_time(new Date());

				//Add the arbitary string in the POST packet
				String msgData = "STRING:"+arbitaryString;
				msgData = "type=msg&to=everyone&message=" +	msgData;				
				packet.setContent_length(msgData.length());
				packet.setData(msgData);
				
				//Send the request to the server
				out.println(packet.toString());
				out.flush();
				
				//Wait for 1 second before sending the vote request
				try {Thread.sleep(1000);} catch(InterruptedException ex) {}
				
			////  Uncomment below commented line to demo timeout by participants while waiting for VOTE_REQUEST
			////  try {Thread.currentThread().wait();} catch(InterruptedException ex) {}
				
				//Send the REQUEST_VOTE message to every participant
				msgData = "REQUEST_VOTE";
				msgData = "type=msg&to=everyone&message=" +	msgData;				
				packet.setContent_length(msgData.length());
				packet.setData(msgData);

				//Send the request to the server
				out.println(packet.toString());
				out.flush();
				
				//Start the timer
				stopwatch = new StopWatch();
				
				//Enter wait state
				state = "WAIT";
				System.out.println("Coordinator:WAIT");
				
				//Initialize a list to record the votes
				receivedVotes = new ArrayList<String>();
			}
		});
	}

	/**
	 * Implements the connection logic by setting 
	 * the server's IP address, connecting and setting up streams
	 */
	public void connectToServer() throws IOException {

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
		//Create an object of the HTTPMultithreadedSocketCoordinator class
		HTTPMultithreadedSocketCoordinator client = new HTTPMultithreadedSocketCoordinator();
		
		//Display the user interface
		client.frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		client.frame.pack();
		client.frame.setVisible(true);
		
		//Connect to the socket server
		client.connectToServer();
		
		//Display the contents of log file (coordinator.txt) on screen
		client.displayOldData();
	}
	
	//This function appends a string to the coordinator.txt file
	public void writeToFile(String line)
	{
		try {
			//Initialize the BufferedWriter
			FileWriter fileWriter = new FileWriter("coordinator.txt", true);
			BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
            bufferedWriter.newLine();
            
            //Append the string 
			bufferedWriter.write(line);
			
			//Close the BufferedWriter
            bufferedWriter.close();
		}
		catch(IOException ex) {
			ex.printStackTrace();
		}
	}
	
	//This function reads the content of coordinator.txt file and displays in on the screen
	public void displayOldData()
	{
		try 
		{
			String line="";
			File logFile = new File("coordinator.txt");
			
			//Check it the file exists
			if(logFile.exists())
			{
				//Initialize the BufferedReader
				FileReader fileReader = new FileReader(logFile);
	            BufferedReader bufferedReader = new BufferedReader(fileReader);
	            
	            //Read every line from the file and display it on screen
	            while((line = bufferedReader.readLine()) != null) 
	            {
					messageArea.append(line + "\n");
	            }   
	            
	            //Scroll down the screen
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