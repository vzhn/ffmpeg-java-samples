# ffmpeg java samples


### Demux and decode h264

* demux MKV file
* decode h264 video stream
* convert yuv420p `AVFrame` to RGB `AVFrame`
* convert `AVFrame` to java `BufferedImage`

see [DemuxAndDecodeH264.java](https://github.com/vzhn/ffmpeg-java-samples/blob/master/src/main/java/DemuxAndDecodeH264.java)

### Encode and mux h264
* draw pictures on java `BufferedImage`
* convert `BufferedImage` to RGB `AVFrame`
* convert RGB `AVFrame` to yuv420p `AVFrame`
* encode `AVFrame` and get sequence of `AVPacket`'s
* mux `AVPackets` to Matroska media container

see [EncodeAndMuxH264.java](https://github.com/vzhn/ffmpeg-java-samples/blob/master/src/main/java/EncodeAndMuxH264.java)


### Grab screen
* get picture data from `x11grab` device
* convert picture data to RGB format
* convert RGB data to java `BufferedImage`

see [GrabScreen.java](https://github.com/vzhn/ffmpeg-java-samples/blob/master/src/main/java/GrabScreen.java)

### USB Webcam capture (Linux)
Works well with my Logitech webcam

* get picture data from `v4l2` device in `mjpeg` format
* convert mjpeg to BufferedImage
* show BufferedImage on JFrame

see [WebcamCapture.java](https://github.com/vzhn/ffmpeg-java-samples/blob/master/src/main/java/WebcamCapture.java)