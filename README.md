# PCVolumeControlAndroid
The Android component of the PcVolumeControl project

Download from:
https://play.google.com/store/apps/details?id=com.darkrockstudios.apps.pcvolumemixer

## PC server
This app speaks protocol version 8 and needs the PcVolumeControl server on your PC:
https://github.com/PcVolumeControl/PcVolumeControlServer

It does not work with the original Windows server (PcVolumeControlWindows), which speaks version 7. Connecting to one shows a message asking you to update the server. Servers found on the local network are listed only when they speak version 8.

## Application icons and details
Applications show their icon beside their name, and a short detail under it when the server sends one, such as a browser tab's title, to tell apart sessions of the same application. Icons are cached on the phone, so they are usually downloaded only once. A plain placeholder takes the icon's place while an icon is loading or when the server has none for an application.
