import org.apache.commons.cli.*;
import org.bytedeco.javacpp.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

import static org.bytedeco.javacpp.avcodec.*;
import static org.bytedeco.javacpp.avformat.*;
import static org.bytedeco.javacpp.avutil.*;
import static org.bytedeco.javacpp.swscale.SWS_BICUBIC;
import static org.bytedeco.javacpp.swscale.sws_getContext;

public final class EncodeAndMuxH264 {
    private final static String DEFAULT_FPS = "30";
    private static final String DEFAULT_BITRATE = "400000";
    private static final String DEFAULT_WIDTH = "640";
    private static final String DEFAULT_HEIGHT = "320";
    private static final String DEFAULT_GOP = "60";
    private static final String DEFAULT_MAX_B_FRAMES = "12";
    private static final String DEFAULT_N_FRAMES = "300";
    private static final String DEFAULT_PROFILE = "baseline";
    private static final String DEFAULT_FILE = "out.mkv";

    private AVFrame frame;
    private AVFrame rgbFrame;
    private swscale.SwsContext swsContext;
    private BufferedImage image;
    private AVCodecContext cc;
    private int fps;
    private int bitrate;
    private int width;
    private int height;
    private int gopSize;
    private int maxBFrames;
    private int nFrames;
    private String profile;
    private AVCodec codec;
    private AVFormatContext oc;
    private AVOutputFormat fmt;
    private String ofile;
    private AVRational streamTimebase;
    private AVRational codecTimebase;

    private EncodeAndMuxH264() {}

    public static void main(String... argv) throws ParseException {
        Options options = new Options();
        options.addOption("help", false, "show help and exit");
        options.addOption("fps", true, "fps");
        options.addOption("bitrate", true, "bitrate");
        options.addOption("width", true, "width");
        options.addOption("height", true, "height");
        options.addOption("gop", true, "gop");
        options.addOption("max_b_frames", true, "max_b_frames");
        options.addOption("n_frames", true, "number of frames");
        options.addOption("profile", true, "h264 profile");
        options.addOption("file", true, "output file name");
        CommandLine cmd = new DefaultParser().parse(options, argv);
        if (cmd.hasOption("help")) {
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp("EncodeAndMuxH264 [options]", options);
        } else {
            System.out.println("options:");
            EncodeAndMuxH264 instance = new EncodeAndMuxH264();
            instance.fps = Integer.parseInt(getOption(cmd, "fps", DEFAULT_FPS));
            instance.bitrate = Integer.parseInt(getOption(cmd, "bitrate", DEFAULT_BITRATE));
            instance.width = Integer.parseInt(getOption(cmd,"width", DEFAULT_WIDTH));
            instance.height = Integer.parseInt(getOption(cmd,"height", DEFAULT_HEIGHT));
            instance.gopSize = Integer.parseInt(getOption(cmd,"gop", DEFAULT_GOP));
            instance.maxBFrames = Integer.parseInt(getOption(cmd,"max_b_frames", DEFAULT_MAX_B_FRAMES));
            instance.nFrames = Integer.parseInt(getOption(cmd,"n_frames", DEFAULT_N_FRAMES));
            instance.profile = getOption(cmd,"profile", DEFAULT_PROFILE);
            instance.ofile = getOption(cmd,"file", DEFAULT_FILE);

            instance.start();
        }
    }

    private static String getOption(CommandLine cmd, String key, String defaultValue) {
        String v = cmd.getOptionValue(key, defaultValue);
        System.out.println("\t" + key + " = \"" + defaultValue + "\"");
        return v;
    }

    private void start() {
        allocCodecContext();

        AVPacket pkt = av_packet_alloc();

        allocFrame(cc);
        allocRgbFrame(cc);
        allocSwsContext();
        allocOutputContext();

        encodeVideo(pkt);
        writeDelayedFrames(pkt);

        av_write_trailer(oc);
        free(cc, oc);
    }

    private void writeDelayedFrames(AVPacket pkt) {
        sendFrame(pkt, null);
    }

    private void encodeVideo(AVPacket pkt) {
        for (int i = 0; i < nFrames; i++) {
            frame.pts(avutil.av_rescale_q(i, codecTimebase, streamTimebase));

            drawFrame(i);
            sendFrame(pkt, frame);
        }
    }

    private void sendFrame(AVPacket pkt, AVFrame o) {
        int r = avcodec.avcodec_send_frame(cc, o);
        if (r == 0) {
            receivePacket(pkt);
        } else {
            throw new RuntimeException("error: " + r);
        }
    }

    private void drawFrame(int n) {
        Graphics gc = image.getGraphics();
        gc.clearRect(0, 0, image.getWidth(), image.getHeight());
        gc.setFont(gc.getFont().deriveFont(50f));
        gc.drawString(String.format("pts: %d", n), 200, 200);
        gc.dispose();

        DataBufferByte dataBufferByte = (DataBufferByte) image.getRaster().getDataBuffer();
        rgbFrame.data(0).put(dataBufferByte.getData());

        swscale.sws_scale(
            swsContext, rgbFrame.data(), rgbFrame.linesize(), 0,
            frame.height(), frame.data(), frame.linesize()
        );
    }

    private void allocOutputContext() {
        oc = new AVFormatContext();
        int r = avformat_alloc_output_context2(oc, null, null, ofile);
        if (r < 0) {
            throw new RuntimeException("could not allocate output context");
        }
        fmt = oc.oformat();
        AVStream st = avformat_new_stream(oc, codec);
        avcodec_parameters_from_context(st.codecpar(), cc);
        st.time_base(cc.time_base());

        av_dump_format(oc, 0, ofile, 1);

        /* open the output file, if needed */
        PointerPointer pp = new PointerPointer(1);
        try {
            if (avio_open(pp, new BytePointer(ofile), AVIO_FLAG_WRITE) <0){
                throw new RuntimeException("Could not open " + fmt);
            }
            oc.pb(new AVIOContext(pp.get()));
        } finally {
            pp.deallocate();
        }

        /* Write the stream header, if any. */
        if (avformat_write_header(oc, (AVDictionary) null) < 0) {
            throw new RuntimeException("Error occurred when opening output file\n");
        }

        streamTimebase = st.time_base();
    }

    private void allocCodecContext() {
        codecTimebase = new avutil.AVRational();
        codecTimebase.num(1);
        codecTimebase.den(fps);
        codec = avcodec_find_encoder(AV_CODEC_ID_H264);
        cc = avcodec_alloc_context3(codec);

        cc.bit_rate(bitrate);
        cc.width(width);
        cc.height(height);
        cc.time_base(codecTimebase);
        cc.gop_size(gopSize);
        cc.max_b_frames(maxBFrames);
        if (profile != null && !"".equals(profile)) {
            av_opt_set(cc.priv_data(), "profile", profile, 0);
        }

        cc.pix_fmt(avutil.AV_PIX_FMT_YUV420P);
        cc.flags(cc.flags() | AV_CODEC_FLAG_GLOBAL_HEADER);
        if (avcodec_open2(cc, codec, (AVDictionary) null) < 0) {
            throw new RuntimeException("could not open codec");
        }
    }

    private void free(AVCodecContext cc, AVFormatContext oc) {
        avcodec_close(cc);
        avcodec_free_context(cc);
        av_free(rgbFrame.data(0));
        av_free(frame.data(0));
        av_free(rgbFrame);
        av_free(frame);

        avio_close(oc.pb());
        av_free(oc);
    }

    private void allocSwsContext() {
        swsContext = sws_getContext(rgbFrame.width(), rgbFrame.height(), rgbFrame.format(),
                frame.width(), frame.height(), frame.format(), SWS_BICUBIC,
                null, null, (DoublePointer) null);

        if (swsContext.isNull()) {
            throw new RuntimeException("Could not init sws context!");
        }
    }

    private void allocRgbFrame(AVCodecContext cc) {
        image = new BufferedImage(cc.width(), cc.height(), BufferedImage.TYPE_3BYTE_BGR);

        rgbFrame = av_frame_alloc();
        rgbFrame.format(AV_PIX_FMT_BGR24);
        rgbFrame.width(cc.width());
        rgbFrame.height(cc.height());
        int ret = av_frame_get_buffer(rgbFrame, 32);
        if (ret < 0) {
            throw new RuntimeException("Could not allocate the video frame data");
        }
    }

    private void allocFrame(AVCodecContext cc) {
        frame = av_frame_alloc();
        frame.format(cc.pix_fmt());
        frame.width(cc.width());
        frame.height(cc.height());
        int ret = av_frame_get_buffer(frame, 32);
        if (ret < 0) {
            throw new RuntimeException("Could not allocate the video frame data");
        }
    }

    private void receivePacket(AVPacket pkt) {
        int r;
        while ((r = avcodec.avcodec_receive_packet(cc, pkt)) == 0) {
            r = av_interleaved_write_frame(oc, pkt);
            av_packet_unref(pkt);
            if (r != 0) {
                throw new RuntimeException("Error while writing video frame\n");
            }
        }

        if (r != AVERROR_EAGAIN() && r != AVERROR_EOF()) {
            throw new RuntimeException("error");
        }
    }
}