# ffmpeg java samples


### Demux and decode h264

* demux MKV file
* decode h264 video stream
* convert yub420p `AVFrame` to RGB `AVFrame`
* convert `AVFrame` to java `BufferedImage`

see [DemuxAndDecodeH264.java](https://github.com/vzhn/ffmpeg-java-samples/blob/master/src/main/java/DemuxAndDecodeH264.java)

### Encode and mux h264
* draw pictures on java `BufferedImage`
* convert `BufferedImage` to RGB `AVFrame`
* convert RGB `AVFrame` to yub420p `AVFrame`
* encode `AVFrame` and get sequence of `AVPacket`'s
* mux `AVPackets` to Matroska media container

see [EncodeAndMuxH264.java](https://github.com/vzhn/ffmpeg-java-samples/blob/master/src/main/java/EncodeAndMuxH264.java)