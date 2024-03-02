package io.github.gaming32.stillalive;

import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.AudioDeviceBase;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

// https://github.com/umjammer/jlayer/blob/master/src/test/java/javazoom/jl/player/my/MyJavaSoundAudioDevice.java
@SuppressWarnings("all")
public class VolumeControlAudioDevice extends AudioDeviceBase {
    private SourceDataLine source = null;
    private AudioFormat fmt = null;
    private byte[] byteBuf = new byte[4096];

    protected void setAudioFormat(AudioFormat fmt0) {
        fmt = fmt0;
    }

    protected AudioFormat getAudioFormat() {
        fmt = new AudioFormat(44100,
            16,
            2,
            true,
            false);

        return fmt;
    }

    protected DataLine.Info getSourceLineInfo() {
        AudioFormat fmt = getAudioFormat();
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
        return info;
    }

    public void open(AudioFormat fmt) throws JavaLayerException {
        if (!isOpen()) {
            setAudioFormat(fmt);
            openImpl();
            setOpen(true);
        }
    }

    /** */
    private float gain = 6.0206f;

    /** @param gain 0 ~ 1 */
    public void setVolume(float gain) {
        this.gain = (float) (Math.log10(gain) * 20.0);
    }

    /** */
    private void setLineGain() {
        FloatControl volControl = (FloatControl) source.getControl(FloatControl.Type.MASTER_GAIN);
        volControl.setValue(gain);
    }

    @Override
    public void openImpl()
        throws JavaLayerException {
    }

    // createSource fix.
    public void createSource() throws JavaLayerException {
        Throwable t = null;
        try {
            Line line = AudioSystem.getLine(getSourceLineInfo());
            if (line instanceof SourceDataLine) {
                source = (SourceDataLine) line;
                source.open(fmt);
                setLineGain();

                source.start();
            }
        } catch (RuntimeException | LinkageError | LineUnavailableException ex) {
            ex.printStackTrace(System.err);
            t = ex;
        }
        if (source == null) {
            throw new JavaLayerException("cannot obtain source audio line", t);
        }
    }

    public int millisecondsToBytes(AudioFormat fmt, int time) {
        return (int) (time * (fmt.getSampleRate() * fmt.getChannels() * fmt.getSampleSizeInBits()) / 8000.0);
    }

    @Override
    protected void closeImpl() {
        if (source != null) {
            source.close();
        }
    }

    @Override
    protected void writeImpl(short[] samples, int offs, int len)
        throws JavaLayerException {
        if (source == null) {
            createSource();
        }

        byte[] b = toByteArray(samples, offs, len);
        source.write(b, 0, len * 2);
    }

    protected byte[] getByteArray(int length) {
        if (byteBuf.length < length) {
            byteBuf = new byte[length + 1024];
        }
        return byteBuf;
    }

    protected byte[] toByteArray(short[] samples, int offs, int len) {
        byte[] b = getByteArray(len * 2);
        int idx = 0;
        short s;
        while (len-- > 0) {
            s = samples[offs++];
            b[idx++] = (byte) s;
            b[idx++] = (byte) (s >>> 8);
        }
        return b;
    }

    @Override
    protected void flushImpl() {
        if (source != null) {
            source.drain();
        }
    }

    @Override
    public int getPosition() {
        int pos = 0;
        if (source != null) {
            pos = (int) (source.getMicrosecondPosition() / 1000);
        }
        return pos;
    }

    /**
     * Runs a short test by playing a short silent sound.
     */
    public void test() throws JavaLayerException {
        open(new AudioFormat(22000, 16, 1, true, false));
        short[] data = new short[22000 / 10];
        write(data, 0, data.length);
        flush();
        close();
    }
}
