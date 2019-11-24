import org.apache.commons.cli.*;
import org.bytedeco.javacpp.*;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

import static java.lang.String.format;
import static org.bytedeco.javacpp.avcodec.av_packet_unref;
import static org.bytedeco.javacpp.avdevice.avdevice_register_all;
import static org.bytedeco.javacpp.avformat.*;
import static org.bytedeco.javacpp.avutil.*;
import static org.bytedeco.javacpp.swscale.sws_freeContext;
import static org.bytedeco.javacpp.swscale.sws_getContext;

public final class GrabScreen {
    /** upper left corner coordinates */
    private static final String DEFAULT_X = "0";
    private static final String DEFAULT_Y = "0";

    /** screen fragment dimensions */
    private static final String DEFAULT_WIDTH = "640";
    private static final String DEFAULT_HEIGHT = "480";

    private int width;
    private int height;
    private int x;
    private int y;
    private String display;

    private AVInputFormat x11grab;
    private AVFormatContext x11GrabDevice;
    private avcodec.AVPacket pkt;
    private BufferedImage bufferedImage;
    private AVFrame rgbFrame;
    private swscale.SwsContext swsContext;
    private IntPointer bgr0Linesize;

    private GrabScreen() {}

    public static void main(String... argv) throws ParseException {
        av_log_set_level(AV_LOG_VERBOSE);

        Options options = new Options();
        options.addOption("help", false, "show help and exit");
        options.addOption("width", true, "width");
        options.addOption("height", true, "height");
        options.addOption("x", true, "x");
        options.addOption("y", true, "y");
        options.addOption("display", true, "display");

        CommandLine cmd = new DefaultParser().parse(options, argv);
        if (cmd.hasOption("help")) {
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp("EncodeAndMuxH264 [options]", options);
        } else {
            System.out.println("options:");
            GrabScreen instance = new GrabScreen();
            instance.width = Integer.parseInt(getOption(cmd,"width", DEFAULT_WIDTH));
            instance.height = Integer.parseInt(getOption(cmd,"height", DEFAULT_HEIGHT));
            instance.x = Integer.parseInt(getOption(cmd,"x", DEFAULT_X));
            instance.y = Integer.parseInt(getOption(cmd,"y", DEFAULT_Y));
            instance.display = getOption(cmd, "display", System.getenv("DISPLAY"));

            instance.start();
        }

        GrabScreen instance = new GrabScreen();
        instance.start();
    }

    private static String getOption(CommandLine cmd, String key, String defaultValue) {
        String v = cmd.getOptionValue(key, defaultValue);
        System.out.println("\t" + key + " = \"" + v + "\"");
        return v;
    }

    private void setupX11GrabDevice() {
        avdevice_register_all();
        x11grab = av_find_input_format("x11grab");
        if (x11grab == null) {
            throw new RuntimeException("x11grab not found");
        }
        x11GrabDevice = avformat_alloc_context();
        if (x11GrabDevice == null) {
            throw new RuntimeException("x11grab device not found");
        }

        String url = format("%s.0+%d,%d", display, x, y);
        AVDictionary options = new AVDictionary();
        av_dict_set(options, "video_size", format("%dx%d", width, height), 0);
        if(avformat_open_input(x11GrabDevice, url, x11grab, options) != 0) {
            throw new RuntimeException("Couldn't open input stream.\n");
        }
        av_dict_free(options);

        av_dump_format(x11GrabDevice, 0, url, 0);
        if (x11GrabDevice.nb_streams() == 0) {
            throw new RuntimeException("Stream not found!");
        }
        AVStream st = x11GrabDevice.streams(0);
        avcodec.AVCodecParameters params = st.codecpar();
        width = params.width();
        height = params.height();
        int pixFormat = params.format();
        if (pixFormat != AV_PIX_FMT_BGR0) {
            throw new RuntimeException("unsupported pixel format: " + pixFormat);
        }
        pkt = new avcodec.AVPacket();
    }

    private void start() {
        setupX11GrabDevice();
        allocRGB24Frame();
        allocSWSContext();

        JFrame frame = setupJFrame();
        PointerPointer<Pointer> pktDataPointer = new PointerPointer<>(1);
        while (frame.isShowing()) {
            av_read_frame(x11GrabDevice, pkt);
            pktDataPointer.put(pkt.data());

            swscale.sws_scale(
                swsContext, pktDataPointer, bgr0Linesize, 0,
                rgbFrame.height(), rgbFrame.data(), rgbFrame.linesize()
            );

            DataBufferByte buffer = (DataBufferByte) bufferedImage.getRaster().getDataBuffer();
            rgbFrame.data(0).get(buffer.getData());
            av_packet_unref(pkt);

            frame.repaint();
        }
        pktDataPointer.deallocate();

        av_frame_free(rgbFrame);
        avformat_close_input(x11GrabDevice);
        sws_freeContext(swsContext);

        frame.dispose();
        System.exit(0);
    }

    private JFrame setupJFrame() {
        this.bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        JFrame frame = new JFrame() {
            @Override
            public void paint(Graphics g) {
                g.drawImage(bufferedImage, 0, 0, null);
            }
        };
        frame.setTitle("grab screen");
        frame.setSize(width, height);
        frame.setVisible(true);
        return frame;
    }

    private void allocRGB24Frame() {
        rgbFrame = av_frame_alloc();
        rgbFrame.format(AV_PIX_FMT_BGR24);
        rgbFrame.width(width);
        rgbFrame.height(height);
        int ret = av_frame_get_buffer(rgbFrame, 32);
        if (ret < 0) {
            throw new RuntimeException("Could not allocate the video frame data");
        }
    }

    private void allocSWSContext() {
        bgr0Linesize = new IntPointer(1);
        bgr0Linesize.put(4 * width);
        swsContext =
            sws_getContext(width, height, AV_PIX_FMT_BGR0,
            width, height, rgbFrame.format(), 0,
                null, null, (DoublePointer) null);
    }
}
