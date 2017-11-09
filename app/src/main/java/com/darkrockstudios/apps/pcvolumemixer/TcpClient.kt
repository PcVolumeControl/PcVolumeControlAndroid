package com.darkrockstudios.apps.pcvolumemixer

/**
 * Created by adamw on 9/16/2017.
 */

import android.util.Log
import java.io.*
import java.net.InetAddress
import java.net.Socket

/**
 * Description
 *
 * @author Catalin Prata
 * Date: 2/12/13
 */
class TcpClient
/**
 * Constructor of the class. OnMessagedReceived listens for the messages received from server
 */
(listener: ServerListener, serverIp: String, port: Int)
{
	private val mServerIp = serverIp
	private val mServerPort = port
	// message to send to the server
	private var mServerMessage: String? = null
	// sends message received notifications
	private val mMessageListener: ServerListener = listener
	// while this is true, the server will continue running
	private var mRun = false
	// used to send messages
	private var mBufferOut: PrintWriter? = null
	// used to read messages from the server
	private var mBufferIn: BufferedReader? = null

	fun isRunning(): Boolean
	{
		return mRun
	}

	/**
	 * Sends the message entered by client to the server
	 *
	 * @param message text entered by client
	 */
	fun sendMessage(message: String)
	{
		if (mBufferOut != null && !mBufferOut!!.checkError())
		{
			mBufferOut!!.println(message)
			mBufferOut!!.flush()
		}
	}

	fun sendMessageAsync(message: String)
	{
		Thread({ sendMessage(message) }).start()
	}

	/**
	 * Close the connection and release the members
	 */
	fun stopClient()
	{
		Log.i("Debug", "stopClient")

		// send mesage that we are closing the connection
		//sendMessage(Constants.CLOSED_CONNECTION + "Kazy");

		mRun = false

		if (mBufferOut != null)
		{
			mBufferOut!!.flush()
			mBufferOut!!.close()
		}

		if( mBufferIn != null )
		{
			mBufferIn!!.close()
		}

		mBufferIn = null
		mBufferOut = null
		mServerMessage = null
	}

	fun run()
	{
		mRun = true

		try
		{
			//here you must put your computer's IP address.
			val serverAddr = InetAddress.getByName(mServerIp)

			Log.e("TCP Client", "C: Connecting...")

			//create a socket to make the connection with the server
			val socket = Socket(serverAddr, mServerPort)

			mMessageListener.onConnect()

			try
			{
				//sends the message to the server
				mBufferOut = PrintWriter(BufferedWriter(OutputStreamWriter(socket.getOutputStream())), true)
				mBufferOut?.apply {
					Log.i("Debug", "sending data")
					//sendMessage("test")
				}

				//receives the message which the server sends back
				mBufferIn = BufferedReader(InputStreamReader(socket.getInputStream()))
				// send login name
				//sendMessage(Constants.LOGIN_NAME + PreferencesManager.getInstance().getUserName());
				//sendMessage("Hi");
				//in this while the client listens for the messages sent by the server
				while (mRun)
				{
					val serverMessage = mBufferIn?.readLine()

					mServerMessage = mServerMessage
					if (serverMessage != null)
					{
						mMessageListener.messageReceived(serverMessage)
					}
				}
				Log.e("RESPONSE FROM SERVER", "S: Received Message: '$mServerMessage'")
			}
			catch (e: Exception)
			{
				Log.e("TCP", "S: Error", e)
			}
			finally
			{
				//the socket must be closed. It is not possible to reconnect to this socket
				// after it is closed, which means a new socket instance has to be created.
				try
				{
					Log.d("TCP", "Closing socket")
					socket.close()
				}
				catch (e: IOException)
				{
					Log.e("TCP", "S: Error", e)
				}
			}
		}
		catch (e: Exception)
		{
			Log.e("TCP", "C: Error", e)
		}
		finally
		{
			mRun = false
			mMessageListener.onDisconnect()
		}
	}

	//Declare the interface. The method messageReceived(String message) must be implemented in the MyActivity
	//class at on asynckTask doInBackground
	interface ServerListener
	{
		fun messageReceived(message: String)
		fun onConnect()
		fun onDisconnect()
	}

	companion object
	{
		val SERVER_PORT = 3000
	}
}