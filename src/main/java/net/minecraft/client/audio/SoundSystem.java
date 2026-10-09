package net.minecraft.client.audio;

import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.AL11.AL_LINEAR_DISTANCE_CLAMPED;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.stb.STBVorbis.*;

/**
 * OpenAL playback, standing in for the paulscode SoundSystem vanilla
 * 1.8.9 drives. Short sounds are decoded whole (Ogg Vorbis through
 * stb_vorbis) and cached per file; "stream" sounds (music) are decoded a
 * second at a time into a queue of buffers. Positional sounds fade linearly
 * to silence at their range (paulscode's linear attenuation); others play
 * at the listener. If no audio device opens, everything is silently
 * skipped. Main thread only.
 */
public final class SoundSystem {
    /** Mixing channels for ordinary sounds; paulscode's default of 28 (HYPOTHESIS). */
    public static final int CHANNELS = 28;
    private static final int STREAM_BUFFERS = 4, STREAM_SAMPLES = 44100;

    private final IResourceManager resources;
    private long device, context;
    private boolean ready;
    private final Map<ResourceLocation, Integer> buffers = new HashMap<>();
    private final List<Channel> channels = new ArrayList<>();
    private int nextHandle = 1;

    /** One OpenAL source and what it is playing. */
    private static final class Channel {
        final int source;
        int handle;
        float baseGain;
        String category;
        Stream stream;
        boolean paused;
        Channel(int source) { this.source = source; }
    }

    /** An Ogg file being decoded as it plays. */
    private static final class Stream {
        final ByteBuffer data;
        final long decoder;
        final int channels, sampleRate;
        final int[] buffers = new int[STREAM_BUFFERS];
        final ShortBuffer pcm;
        boolean ended;

        Stream(ByteBuffer data, long decoder, int channels, int sampleRate) {
            this.data = data;
            this.decoder = decoder;
            this.channels = channels;
            this.sampleRate = sampleRate;
            pcm = MemoryUtil.memAllocShort(STREAM_SAMPLES * channels);
        }

        /** Decodes the next piece into an OpenAL buffer; false at the end of the file. */
        boolean fill(int buffer) {
            pcm.clear();
            int samples = stb_vorbis_get_samples_short_interleaved(decoder, channels, pcm);
            if (samples <= 0) {
                ended = true;
                return false;
            }
            pcm.limit(samples * channels);
            alBufferData(buffer, channels == 1 ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16, pcm, sampleRate);
            return true;
        }

        void close() {
            stb_vorbis_close(decoder);
            MemoryUtil.memFree(pcm);
            MemoryUtil.memFree(data);
            alDeleteBuffers(buffers);
        }
    }

    public SoundSystem(IResourceManager resources) {
        this.resources = resources;
    }

    /** Opens the default audio device; stays silent (and says so once) if there is none. */
    public void init() {
        try {
            device = alcOpenDevice((ByteBuffer) null);
            if (device == MemoryUtil.NULL) {
                System.err.println("No audio device: sound is off");
                return;
            }
            context = alcCreateContext(device, (IntBuffer) null);
            alcMakeContextCurrent(context);
            ALCCapabilities capabilities = ALC.createCapabilities(device);
            AL.createCapabilities(capabilities);
            alDistanceModel(AL_LINEAR_DISTANCE_CLAMPED);
            for (int i = 0; i < CHANNELS + 2; i++) channels.add(new Channel(alGenSources()));
            ready = true;
        } catch (RuntimeException | UnsatisfiedLinkError error) {
            System.err.println("Could not start audio: " + error);
        }
    }

    public boolean isReady() { return ready; }

    /**
     * Plays a sound file; returns a handle for {@link #isPlaying}, or 0 when
     * nothing plays (no device, unreadable file, every stream channel busy).
     * With every ordinary channel busy the oldest sound is cut off for it.
     *
     * @param range distance at which a positional sound has faded out; 0 for a non-positional sound
     */
    public int play(ResourceLocation file, boolean stream, String category, float gain, float pitch,
                    double x, double y, double z, float range, float categoryGain) {
        if (!ready) return 0;
        Channel channel = freeChannel(stream);
        if (channel == null) return 0;
        int source = channel.source;
        alSourceStop(source);
        alSourcei(source, AL_BUFFER, 0);
        if (stream) {
            Stream opened = openStream(file);
            if (opened == null) return 0;
            int queued = 0;
            for (int i = 0; i < STREAM_BUFFERS; i++) {
                opened.buffers[i] = alGenBuffers();
                if (opened.fill(opened.buffers[i])) {
                    alSourceQueueBuffers(source, opened.buffers[i]);
                    queued++;
                }
            }
            if (queued == 0) {
                opened.close();
                return 0;
            }
            channel.stream = opened;
        } else {
            Integer buffer = buffer(file);
            if (buffer == null) return 0;
            alSourcei(source, AL_BUFFER, buffer);
        }
        alSourcef(source, AL_PITCH, pitch);
        alSourcei(source, AL_LOOPING, AL_FALSE);
        if (range > 0) {
            alSourcei(source, AL_SOURCE_RELATIVE, AL_FALSE);
            alSource3f(source, AL_POSITION, (float) x, (float) y, (float) z);
            alSourcef(source, AL_REFERENCE_DISTANCE, 0f);
            alSourcef(source, AL_MAX_DISTANCE, range);
            alSourcef(source, AL_ROLLOFF_FACTOR, 1f);
        } else {
            alSourcei(source, AL_SOURCE_RELATIVE, AL_TRUE);
            alSource3f(source, AL_POSITION, 0f, 0f, 0f);
            alSourcef(source, AL_ROLLOFF_FACTOR, 0f);
        }
        channel.baseGain = gain;
        channel.category = category;
        channel.handle = nextHandle++;
        channel.paused = false;
        alSourcef(source, AL_GAIN, Math.min(1f, gain * categoryGain));
        alSourcePlay(source);
        return channel.handle;
    }

    /**
     * Ordinary sounds share the first channels; the last two are kept for
     * streams. When every ordinary channel is busy the one playing longest
     * is taken over, as paulscode interrupts its oldest non-priority source,
     * so a burst of sounds never silences the newest ones.
     */
    private Channel freeChannel(boolean stream) {
        int from = stream ? CHANNELS : 0, to = stream ? channels.size() : CHANNELS;
        Channel oldest = null;
        for (int i = from; i < to; i++) {
            Channel channel = channels.get(i);
            if (!busy(channel)) {
                release(channel);
                return channel;
            }
            if (!stream && !channel.paused && (oldest == null || channel.handle < oldest.handle)) oldest = channel;
        }
        if (oldest != null) release(oldest);
        return oldest;
    }

    private boolean busy(Channel channel) {
        if (channel.handle == 0) return false;
        if (channel.paused) return true;
        if (channel.stream != null && !channel.stream.ended) return true;
        return alGetSourcei(channel.source, AL_SOURCE_STATE) == AL_PLAYING;
    }

    private void release(Channel channel) {
        alSourceStop(channel.source);
        alSourcei(channel.source, AL_BUFFER, 0);
        if (channel.stream != null) {
            channel.stream.close();
            channel.stream = null;
        }
        channel.handle = 0;
        channel.paused = false;
    }

    public boolean isPlaying(int handle) {
        if (!ready || handle == 0) return false;
        for (Channel channel : channels) if (channel.handle == handle) return busy(channel);
        return false;
    }

    /** Refills streams, re-applies category volumes and finishes streams that ran out. Call every frame. */
    public void update(java.util.function.ToDoubleFunction<String> categoryGain) {
        if (!ready) return;
        for (Channel channel : channels) {
            if (channel.handle == 0) continue;
            alSourcef(channel.source, AL_GAIN, (float) Math.min(1.0, channel.baseGain * categoryGain.applyAsDouble(channel.category)));
            Stream stream = channel.stream;
            if (stream == null || channel.paused) continue;
            int processed = alGetSourcei(channel.source, AL_BUFFERS_PROCESSED);
            for (int i = 0; i < processed; i++) {
                int buffer = alSourceUnqueueBuffers(channel.source);
                if (!stream.ended && stream.fill(buffer)) alSourceQueueBuffers(channel.source, buffer);
            }
            int state = alGetSourcei(channel.source, AL_SOURCE_STATE);
            int queued = alGetSourcei(channel.source, AL_BUFFERS_QUEUED);
            if (state != AL_PLAYING) {
                if (queued > 0) alSourcePlay(channel.source);
                else release(channel);
            }
        }
    }

    public void setListener(float x, float y, float z, float lookX, float lookY, float lookZ, float upX, float upY, float upZ) {
        if (!ready) return;
        alListener3f(AL_POSITION, x, y, z);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            alListenerfv(AL_ORIENTATION, stack.floats(lookX, lookY, lookZ, upX, upY, upZ));
        }
    }

    public void setMasterGain(float gain) {
        if (ready) alListenerf(AL_GAIN, gain);
    }

    public void stop(int handle) {
        if (!ready) return;
        for (Channel channel : channels) if (channel.handle == handle) release(channel);
    }

    public void stopAll() {
        if (!ready) return;
        for (Channel channel : channels) release(channel);
    }

    public void pauseAll() {
        if (!ready) return;
        for (Channel channel : channels)
            if (channel.handle != 0 && alGetSourcei(channel.source, AL_SOURCE_STATE) == AL_PLAYING) {
                alSourcePause(channel.source);
                channel.paused = true;
            }
    }

    public void resumeAll() {
        if (!ready) return;
        for (Channel channel : channels)
            if (channel.paused) {
                alSourcePlay(channel.source);
                channel.paused = false;
            }
    }

    /** Stops everything and forgets the decoded sounds so the next play reads them from the new resource packs. */
    public void flushCache() {
        if (!ready) return;
        stopAll();
        for (Integer buffer : buffers.values()) if (buffer != null) alDeleteBuffers(buffer);
        buffers.clear();
    }

    public void destroy() {
        if (!ready) return;
        stopAll();
        for (Channel channel : channels) alDeleteSources(channel.source);
        for (Integer buffer : buffers.values()) if (buffer != null) alDeleteBuffers(buffer);
        buffers.clear();
        alcMakeContextCurrent(MemoryUtil.NULL);
        alcDestroyContext(context);
        alcCloseDevice(device);
        ready = false;
    }

    private Integer buffer(ResourceLocation file) {
        if (buffers.containsKey(file)) return buffers.get(file);
        Integer result = null;
        ByteBuffer data = read(file);
        if (data != null) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer channelCount = stack.mallocInt(1), rate = stack.mallocInt(1);
                ShortBuffer pcm = stb_vorbis_decode_memory(data, channelCount, rate);
                if (pcm != null) {
                    int buffer = alGenBuffers();
                    alBufferData(buffer, channelCount.get(0) == 1 ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16, pcm, rate.get(0));
                    MemoryUtil.memFree(pcm);
                    result = buffer;
                } else {
                    System.err.println("Could not decode sound " + file);
                }
            } finally {
                MemoryUtil.memFree(data);
            }
        }
        buffers.put(file, result);
        return result;
    }

    private Stream openStream(ResourceLocation file) {
        ByteBuffer data = read(file);
        if (data == null) return null;
        try (MemoryStack stack = MemoryStack.stackPush(); STBVorbisInfo info = STBVorbisInfo.malloc()) {
            IntBuffer error = stack.mallocInt(1);
            long decoder = stb_vorbis_open_memory(data, error, null);
            if (decoder == MemoryUtil.NULL) {
                System.err.println("Could not open sound stream " + file + " (" + error.get(0) + ")");
                MemoryUtil.memFree(data);
                return null;
            }
            stb_vorbis_get_info(decoder, info);
            return new Stream(data, decoder, info.channels(), info.sample_rate());
        }
    }

    /** The file's bytes in native memory (freed by the caller), or null if it can't be read. */
    private ByteBuffer read(ResourceLocation file) {
        if (!resources.exists(file)) {
            System.err.println("Missing sound file " + file);
            return null;
        }
        try (InputStream input = resources.open(file)) {
            byte[] bytes = input.readAllBytes();
            ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
            buffer.put(bytes).flip();
            return buffer;
        } catch (IOException error) {
            System.err.println("Could not read sound " + file + ": " + error.getMessage());
            return null;
        }
    }

}
