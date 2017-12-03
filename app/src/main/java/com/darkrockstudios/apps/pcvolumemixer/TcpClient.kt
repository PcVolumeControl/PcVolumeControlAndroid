package com.darkrockstudios.apps.pcvolumemixer

/**
 * Created by adamw on 9/16/2017.
 */

import android.util.Log
import java.io.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

class TcpClient(listener: ServerListener, serverIp: String, port: Int)
{
	private val mServerIp = serverIp
	private val mServerPort = port
	private val mMessageListener: ServerListener = listener

	private var m_run = false

	private var m_bufferOut: PrintWriter? = null
	private var m_bufferIn: BufferedReader? = null

	fun isRunning(): Boolean
			= m_run

	/**
	 * Sends the message entered by client to the server
	 *
	 * @param message text entered by client
	 */
	fun sendMessage(message: String)
	{
		val bufferOut = m_bufferOut
		bufferOut?.let {
			if (!bufferOut.checkError())
			{
				bufferOut.println(message)
				bufferOut.flush()
			}
		}
	}

	fun sendMessageAsync(message: String)
	{
		Log.d(TAG, "C: Sending: " + message)
		Thread({ sendMessage(message) }).start()
	}

	/**
	 * Close the connection and release the members
	 */
	fun stopClient()
	{
		Log.i(TAG, "stopClient")

		// send mesage that we are closing the connection
		//sendMessage(Constants.CLOSED_CONNECTION + "Kazy");

		m_run = false

		m_bufferOut?.apply {
			flush()
			close()
		}

		m_bufferIn?.apply {
			close()
		}

		m_bufferIn = null
		m_bufferOut = null
	}

	fun run()
	{
		m_run = true

		try
		{
			//here you must put your computer's IP address.
			val serverAddr = InetAddress.getByName(mServerIp)

			Log.i(TAG, "C: Connecting... " + mServerIp)

			//create a socket to make the connection with the server
			val socket = Socket()
			socket.connect(InetSocketAddress(serverAddr, mServerPort), 2000)

			if(socket.isConnected)
			{
				mMessageListener.onConnect()

				try
				{
					//sends the message to the server
					m_bufferOut = PrintWriter(BufferedWriter(OutputStreamWriter(socket.getOutputStream())), true)

					//receives the message which the server sends back
					m_bufferIn = BufferedReader(InputStreamReader(socket.getInputStream()))

					//in this while the client listens for the messages sent by the server
					while (m_run)
					{
						val serverMessage = m_bufferIn?.readLine()
						if (serverMessage != null)
						{
							Log.d(TAG, "S: Received Message: '$serverMessage'")
							mMessageListener.messageReceived(serverMessage)
						}
						else
						{
							m_run = false
						}
					}
				}
				catch (e: Exception)
				{
					Log.e(TAG, "S: Error", e)
				}
				finally
				{
					//the socket must be closed. It is not possible to reconnect to this socket
					// after it is closed, which means a new socket instance has to be created.
					try
					{
						Log.d(TAG, "Closing socket")
						m_bufferOut?.flush()
						m_bufferOut?.close()
						socket.close()
					}
					catch (e: IOException)
					{
						Log.e(TAG, "S: Error", e)
					}
				}
			}
		}
		catch (e: Exception)
		{
			Log.e(TAG, "C: Error", e)
		}
		finally
		{
			m_run = false
			mMessageListener.onDisconnect()
		}

		Log.i(TAG, "C: Disconnected.")
	}

	interface ServerListener
	{
		fun messageReceived(message: String)
		fun onConnect()
		fun onDisconnect()
	}

	companion object
	{
		val TAG = TcpClient::class.java.simpleName
	}
}