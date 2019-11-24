import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.avcodec;
import org.bytedeco.javacpp.swscale;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

import static javax.swing.WindowConstants.EXIT_ON_CLOSE;
import static org.bytedeco.javacpp.Pointer.memcpy;
import static org.bytedeco.javacpp.avdevice.avdevice_register_all;
import static org.bytedeco.javacpp.avformat.*;
import static org.bytedeco.javacpp.avutil.*;
import static org.bytedeco.javacpp.swscale.sws_getContext;

public class GrabScreen {
    private int fps = 25;
    private AVInputFormat ifmt;
    private AVFormatContext x11GrabDevice;
    private avcodec.AVPacket pkt;
    private BufferedImage bufferedImage;
    private int width;
    private int height;
    private AVFrame bgr0Frame;
    private AVFrame rgbFrame;
    private swscale.SwsContext swsContext;

    public static void main(String... argv) {
        new GrabScreen().start();
    }

    private void start() {
        setupX11GrabDevice();
        allocBGR0Frame();
        allocRGB24Frame();
        allocSWSContext();

        this.bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        JFrame frame = new JFrame() {
            @Override
            public void paint(Graphics g) {
                g.drawImage(bufferedImage, 0, 0, null);
            }
        };
        frame.setSize(width, height);
        frame.setVisible(true);
        frame.setDefaultCloseOperation(EXIT_ON_CLOSE);

        pkt = new avcodec.AVPacket();

        while (true) {
            av_read_frame(x11GrabDevice, pkt);
            memcpy(bgr0Frame.data(0), pkt.data(), pkt.size());

            swscale.sws_scale(
                swsContext, bgr0Frame.data(), bgr0Frame.linesize(), 0,
                rgbFrame.height(), rgbFrame.data(), rgbFrame.linesize()
            );

            DataBufferByte buffer = (DataBufferByte) bufferedImage.getRaster().getDataBuffer();
            rgbFrame.data(0).get(buffer.getData());

            frame.repaint();
        }
    }

    private void allocSWSContext() {
        swsContext =
            sws_getContext(width, height, bgr0Frame.format(),
            width, height, rgbFrame.format(), 0,
                null, null, (DoublePointer) null);
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

    private void allocBGR0Frame() {
        bgr0Frame = av_frame_alloc();
        bgr0Frame.format(AV_PIX_FMT_BGR0);
        bgr0Frame.width(width);
        bgr0Frame.height(height);
        int ret = av_frame_get_buffer(bgr0Frame, 32);
        if (ret < 0) {
            throw new RuntimeException("Could not allocate the video frame data");
        }
    }

    private void setupX11GrabDevice() {
        avdevice_register_all();
        ifmt = av_find_input_format("x11grab");
        x11GrabDevice = avformat_alloc_context();

        String url = ":1.0+10,20";
        if(avformat_open_input(x11GrabDevice, url,ifmt, null) != 0) {
            throw new RuntimeException("Couldn't open input stream.\n");
        }

        av_dump_format(x11GrabDevice, 0, url, 0);

        AVStream st = x11GrabDevice.streams(0);
        avcodec.AVCodecParameters params = st.codecpar();
        width = params.width();
        height = params.height();
        int pixFormat = params.format();
        if (pixFormat != AV_PIX_FMT_BGR0) {
            throw new RuntimeException("unsupported pixel format: " + pixFormat);
        }
    }
}
