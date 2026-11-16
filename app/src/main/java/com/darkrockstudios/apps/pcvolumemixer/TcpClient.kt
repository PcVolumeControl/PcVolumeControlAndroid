package com.darkrockstudios.apps.pcvolumemixer

/**
 * Created by adamw on 9/16/2017.
 */

import android.util.Log
import com.darkrockstudios.apps.pcvolumemixer.protocol.BoundedLineReader
import java.io.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

class TcpClient(listener: ServerListener, serverIp: String, port: Int)
{
	private val m_serverIp = serverIp
	private val m_serverPort = port
	private val m_serverListener = listener

	private var m_run = false

	private var m_bufferOut: PrintWriter? = null
	private var m_bufferIn: BufferedReader? = null
	private var m_lineReader: BoundedLineReader? = null

	private val m_executor = Executors.newSingleThreadExecutor()

	fun isRunning(): Boolean
			= m_run

	/**
	 * Sends the message entered by client to the server
	 *
	 * @param message text entered by client
	 */
	fun sendMessage(message: String)
	{
		Log.d(TAG, "C: Sending: " + message)

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
		m_executor.submit({ sendMessage(message) })
	}

	/**
	 * Close the connection and release the members
	 */
	fun stopClient()
	{
		Log.i(TAG, "stopClient")

		m_run = false

		m_bufferOut?.apply {
			flush()
			close()
		}

		m_bufferIn?.apply {
			close()
		}

		m_bufferIn = null
		m_lineReader = null
		m_bufferOut = null
	}

	fun run()
	{
		m_serverListener.onConnecting()

		m_run = true

		try
		{
			val serverAddr = InetAddress.getByName(m_serverIp)

			Log.i(TAG, "C: Connecting... " + m_serverIp)

			// Create a socket to make the connection with the server
			val socket = Socket()
			socket.connect(InetSocketAddress(serverAddr, m_serverPort), TIMOUT_MS)

			if(socket.isConnected)
			{
				try
				{
					m_bufferOut = PrintWriter(BufferedWriter(OutputStreamWriter(socket.getOutputStream())), true)
					val bufferIn = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
					m_bufferIn = bufferIn
					m_lineReader = BoundedLineReader(bufferIn)

					m_serverListener.onConnect()

					// Start listening for data from the server
					while (m_run)
					{
						val serverMessage = m_lineReader?.readLine()
						if (serverMessage != null)
						{
							Log.d(TAG, "S: Received Message: '${serverMessage.take(LOG_MESSAGE_CHARS)}'")
							m_serverListener.messageReceived(this, serverMessage)
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
					// Socket died, make sure we clean everything up
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
			m_serverListener.onDisconnect(this)
		}

		Log.i(TAG, "C: Disconnected.")
	}

	interface ServerListener
	{
		fun onConnecting()
		fun onConnect()
		fun messageReceived(client: TcpClient, message: String)
		fun onDisconnect(client: TcpClient)
	}

	companion object
	{
		val TAG = TcpClient::class.java.simpleName
		val TIMOUT_MS = 2000

		// Icon lines can be hundreds of KiB, far more than is useful in a log line.
		private const val LOG_MESSAGE_CHARS = 500
	}
}