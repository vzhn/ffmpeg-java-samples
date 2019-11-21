import org.bytedeco.javacpp.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static org.bytedeco.javacpp.avcodec.*;
import static org.bytedeco.javacpp.avformat.*;
import static org.bytedeco.javacpp.avutil.*;
import static org.bytedeco.javacpp.presets.avutil.AVERROR_EAGAIN;

/**
* Read and decode h264 video from matroska (MKV) container
*/
public final class DemuxAndDecodeH264 {
    /** Matroska format context */
    private AVFormatContext avfmtCtx;

    /** Matroska video stream information  */
    private AVStream videoStream;

    /** matroska packet */
    private AVPacket avpacket;

    /** H264 Decoder ID */
    private AVCodec codec;

    /** H264 Decoder context */
    private AVCodecContext codecContext;

    /** yuv420 frame */
    private AVFrame yuv420Frame;

    /** RGB frame */
    private AVFrame rgbFrame;

    /** java RGB frame */
    private BufferedImage img;

    /** yuv420 to rgb converter */
    private swscale.SwsContext sws_ctx;

    /** number of frame */
    private int nframe;

    /* 1/1000 of second */
    private AVRational tb1000;

    private DemuxAndDecodeH264() {
        tb1000 = new AVRational();
        tb1000.num(1);
        tb1000.den(1000);
    }

    public static void main(String... argv) throws IOException {
        new DemuxAndDecodeH264().start(argv);
    }

    private void start(String[] argv) throws IOException {
        av_log_set_level(AV_LOG_VERBOSE);

        openInput(argv[0]);
        findVideoStream();
        initDecoder();
        initRgbFrame();
        initYuv420Frame();
        getSwsContext();

        avpacket = new avcodec.AVPacket();
        while ((av_read_frame(avfmtCtx, avpacket)) >= 0) {
            processAVPacket(avpacket);
            av_packet_unref(avpacket);
        }
        // now process delayed frames
        processAVPacket(null);
        free();
    }

    private AVFormatContext openInput(String file) throws IOException {
        avfmtCtx = new AVFormatContext(null);
        BytePointer filePointer = new BytePointer(file);
        int r = avformat.avformat_open_input(avfmtCtx, filePointer, null, null);
        filePointer.deallocate();
        if (r < 0) {
            avfmtCtx.close();
            throw new IOException("avformat_open_input error: " + r);
        }
        return avfmtCtx;
    }

    private void findVideoStream() throws IOException {
        int r = avformat_find_stream_info(avfmtCtx, (PointerPointer) null);
        if (r < 0) {
            avformat_close_input(avfmtCtx);
            avfmtCtx.close();
            throw new IOException("error: " + r);
        }

        PointerPointer<AVCodec> decoderRet = new PointerPointer<>(1);
        int videoStreamNumber = av_find_best_stream(avfmtCtx, AVMEDIA_TYPE_VIDEO, -1, -1, decoderRet, 0);
        if (videoStreamNumber < 0) {
            throw new IOException("failed to find video stream");
        }

        if (decoderRet.get(AVCodec.class).id() != AV_CODEC_ID_H264) {
            throw new IOException("failed to find h264 stream");
        }
        decoderRet.deallocate();
        videoStream =  avfmtCtx.streams(videoStreamNumber);
    }

    private void initDecoder() {
        codec = avcodec_find_decoder(AV_CODEC_ID_H264);
        codecContext = avcodec_alloc_context3(codec);
        if((codec.capabilities() & avcodec.AV_CODEC_CAP_TRUNCATED) != 0) {
            codecContext.flags(codecContext.flags() | avcodec.AV_CODEC_CAP_TRUNCATED);
        }
        avcodec_parameters_to_context(codecContext, videoStream.codecpar());
        if(avcodec_open2(codecContext, codec, (PointerPointer) null) < 0) {
            throw new RuntimeException("Error: could not open codec.\n");
        }
    }

    private void initYuv420Frame() {
        yuv420Frame = av_frame_alloc();
        if (yuv420Frame == null) {
            throw new RuntimeException("Could not allocate video frame\n");
        }
    }

    private void initRgbFrame() {
        rgbFrame = av_frame_alloc();
        rgbFrame.format(AV_PIX_FMT_RGB24);
        rgbFrame.width(codecContext.width());
        rgbFrame.height(codecContext.height());
        int ret = av_image_alloc(rgbFrame.data(),
                rgbFrame.linesize(),
                rgbFrame.width(),
                rgbFrame.height(),
                rgbFrame.format(),
                32);
        if (ret < 0) {
            throw new RuntimeException("could not allocate buffer!");
        }
        img = new BufferedImage(rgbFrame.width(), rgbFrame.height(), BufferedImage.TYPE_3BYTE_BGR);
    }

    private void getSwsContext() {
        sws_ctx = swscale.sws_getContext(
                codecContext.width(), codecContext.height(), codecContext.pix_fmt(),
                rgbFrame.width(), rgbFrame.height(), rgbFrame.format(),
                0, null, null, (DoublePointer) null);
    }

    private void processAVPacket(AVPacket avpacket) throws IOException {
        int ret = avcodec.avcodec_send_packet(codecContext, avpacket);
        if (ret < 0) {
            throw new RuntimeException("Error sending a packet for decoding\n");
        }
        receiveFrames();
    }

    private void receiveFrames() throws IOException {
        int ret = 0;
        while (ret >= 0) {
            ret = avcodec.avcodec_receive_frame(codecContext, yuv420Frame);
            if (ret == AVERROR_EAGAIN() || ret == AVERROR_EOF()) {
                continue;
            } else
            if (ret < 0) {
                throw new RuntimeException("error during decoding");
            }
            swscale.sws_scale(sws_ctx, yuv420Frame.data(), yuv420Frame.linesize(), 0,
                    yuv420Frame.height(), rgbFrame.data(), rgbFrame.linesize());

            rgbFrame.best_effort_timestamp(yuv420Frame.best_effort_timestamp());
            processFrame(rgbFrame);
        }
    }

    private void processFrame(AVFrame rgbFrame) throws IOException {
        DataBufferByte buffer = (DataBufferByte) img.getRaster().getDataBuffer();
        rgbFrame.data(0).get(buffer.getData());

        long ptsMillis = av_rescale_q(rgbFrame.best_effort_timestamp(), videoStream.time_base(), tb1000);
        Duration d = Duration.of(ptsMillis, ChronoUnit.MILLIS);

        String name = String.format("img_%05d_%02d-%02d-%02d-%03d.png", ++nframe,
                d.toHoursPart(),
                d.toMinutesPart(),
                d.toSecondsPart(),
                d.toMillisPart());
        ImageIO.write(img, "png", new File(name));
    }

    private void free() {
        av_packet_unref(avpacket);
        avcodec.avcodec_close(codecContext);
        avcodec.avcodec_free_context(codecContext);

        swscale.sws_freeContext(sws_ctx);
        av_frame_free(rgbFrame);
        av_frame_free(yuv420Frame);
        avformat.avformat_close_input(avfmtCtx);
        avformat.avformat_free_context(avfmtCtx);
    }
}
