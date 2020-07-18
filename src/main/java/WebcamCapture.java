import org.bytedeco.javacpp.avcodec;
import util.Canvas;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.bytedeco.javacpp.avcodec.av_packet_unref;
import static org.bytedeco.javacpp.avdevice.avdevice_register_all;
import static org.bytedeco.javacpp.avformat.*;
import static org.bytedeco.javacpp.avutil.*;

public class WebcamCapture {
  public static final int WIDTH = 800;
  public static final int HEIGHT = 600;
  private Canvas canvas;

  public static void main(String... argv) throws IOException {
    new WebcamCapture().start();
  }

  private void start() throws IOException {
    JFrame frame = new JFrame();
    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    frame.setLayout(new BorderLayout());
    canvas = new Canvas();
    frame.add(canvas, BorderLayout.CENTER);
    frame.setVisible(true);
    frame.setSize(WIDTH, HEIGHT);
    startCapture();
  }

  private void startCapture() throws IOException {
    av_log_set_level(AV_LOG_VERBOSE);
    avdevice_register_all();
    AVInputFormat v4l2 = av_find_input_format("v4l2");
    if (v4l2 == null) {
      throw new RuntimeException("v4l2 not found");
    }
    AVFormatContext v4l2Device = avformat_alloc_context();
    if (v4l2Device == null) {
      throw new RuntimeException("failed to alloc AVFormatContext");
    }

    AVDictionary options = new AVDictionary();
    av_dict_set(options, "input_format", "mjpeg", 0);

    if(avformat_open_input(v4l2Device, "/dev/video0", v4l2, options) != 0) {
      throw new RuntimeException("Couldn't open input stream.\n");
    }
    av_dict_free(options);

    av_dump_format(v4l2Device, 0, "", 0);
    if (v4l2Device.nb_streams() == 0) {
      throw new RuntimeException("Stream not found!");
    }

    avcodec.AVPacket pkt = new avcodec.AVPacket();
    while (true) {
      av_read_frame(v4l2Device, pkt);
      byte[] data = new byte[pkt.size()];
      pkt.data().get(data);
      av_packet_unref(pkt);

      canvas.setImage(ImageIO.read(new ByteArrayInputStream(data)));
    }
  }
}
