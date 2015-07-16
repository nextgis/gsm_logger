package com.nextgis.logger.engines;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;
import android.os.Process;
import android.os.SystemClock;

import com.nextgis.logger.util.Constants;

public class AudioEngine extends BaseEngine {
    private AudioMeter mAudioMeter;

    public AudioEngine(Context context) {
        super(context);
        mAudioMeter = new AudioMeter();
    }

    @Override
    public void onPause() {
        mAudioMeter.stopRecording();
    }

    @Override
    public void onResume() {
        if (isAudioEnabled())
            mAudioMeter.startRecording();
    }

    public boolean isAudioEnabled() {
        return getPreferences().getBoolean(Constants.PREF_MIC, true);
    }

    public int getDb() {
        return (int) mAudioMeter.getAmplitude();
    }

    public boolean isRecording() {
        return mAudioMeter.mLocks > 0;
    }

    // http://michaelpardo.com/android/2012/03/recording-audio-streams/
    private class AudioMeter extends Thread {
        /////////////////////////////////////////////////////////////////
        // PUBLIC CONSTANTS

        // Convenience constants
        public static final int AMP_SILENCE = 0;
        public static final int AMP_NORMAL_BREATHING = 10;
        public static final int AMP_MOSQUITO = 20;
        public static final int AMP_WHISPER = 30;
        public static final int AMP_STREAM = 40;
        public static final int AMP_QUIET_OFFICE = 50;
        public static final int AMP_NORMAL_CONVERSATION = 60;
        public static final int AMP_HAIR_DRYER = 70;
        public static final int AMP_GARBAGE_DISPOSAL = 80;

        /////////////////////////////////////////////////////////////////
        // PRIVATE CONSTANTS

        private static final float MAX_REPORTABLE_AMP = 32767f;
        private static final float MAX_REPORTABLE_DB = 90.3087f;

        /////////////////////////////////////////////////////////////////
        // PRIVATE MEMBERS

        private AudioRecord mAudioRecord;
        private int mSampleRate;
        private short mAudioFormat;
        private short mChannelConfig;

        private short[] mBuffer;
        private int mBufferSize = AudioRecord.ERROR_BAD_VALUE;

        private int mLocks = 0;

        /////////////////////////////////////////////////////////////////
        // CONSTRUCTOR

        public AudioMeter() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
            createAudioRecord();
        }

        /////////////////////////////////////////////////////////////////
        // PUBLIC METHODS

        public float getAmplitude() {
            float rawAmplitude = getMaxRawAmplitude();
            if (rawAmplitude == 0f)
                rawAmplitude = getRawAmplitude();
            if (rawAmplitude == 0f)
                rawAmplitude = 1;

            return (float) (MAX_REPORTABLE_DB + (20 * Math.log10(rawAmplitude / MAX_REPORTABLE_AMP)));
        }

        public synchronized void startRecording() {
            if (isAudioRecordInvalid()) {
                createAudioRecord();
            }

            if (mLocks == 0) {
                if (isAudioRecordInvalid())
                    throw new IllegalStateException("startRecording() called on an uninitialized AudioRecord.");

                mAudioRecord.startRecording();

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        while (isRecording()) {
                            SystemClock.sleep(Constants.UPDATE_FREQUENCY);
                            notifyListeners();
                        }
                    }
                }).start();
            }

            mLocks++;
        }

        private boolean isAudioRecordInvalid() {
            return mAudioRecord == null || mAudioRecord.getState() != AudioRecord.STATE_INITIALIZED;
        }

        public synchronized void stopRecording() {
            mLocks--;

            if (mLocks == 0) {
                if (mAudioRecord != null) {
                    mAudioRecord.stop();
                    mAudioRecord.release();
                    mAudioRecord = null;
                }
            }
        }

        /////////////////////////////////////////////////////////////////
        // PRIVATE METHODS

        private void createAudioRecord() {
            if (mSampleRate > 0 && mAudioFormat > 0 && mChannelConfig > 0) {
                mAudioRecord = new AudioRecord(AudioSource.MIC, mSampleRate, mChannelConfig, mAudioFormat, mBufferSize);
                return;
            }

            // Find best/compatible AudioRecord
            for (int sampleRate : new int[] { 8000, 11025, 16000, 22050, 32000, 44100, 47250, 48000 }) {
                for (short audioFormat : new short[] { AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_8BIT }) {
                    for (short channelConfig : new short[] { AudioFormat.CHANNEL_IN_MONO, AudioFormat.CHANNEL_IN_STEREO,
                            AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.CHANNEL_CONFIGURATION_STEREO }) {

                        // Try to initialize
                        try {
                            mBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);

                            if (mBufferSize < 0) {
                                continue;
                            }

                            mBuffer = new short[mBufferSize];
                            mAudioRecord = new AudioRecord(AudioSource.MIC, sampleRate, channelConfig, audioFormat,	mBufferSize);

                            if (mAudioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                                mSampleRate = sampleRate;
                                mAudioFormat = audioFormat;
                                mChannelConfig = channelConfig;

                                return;
                            }

                            mAudioRecord.release();
                            mAudioRecord = null;
                        }
                        catch (Exception e) {
                            // Do nothing
                        }
                    }
                }
            }
        }

        private int getRawAmplitude() {
            if (mAudioRecord == null) {
                createAudioRecord();
            }

            final int bufferReadSize = mAudioRecord.read(mBuffer, 0, mBufferSize);

            if (bufferReadSize < 0) {
                return 0;
            }

            double sum = 0;
//        int sum = 0;
            for (int i = 0; i < bufferReadSize; i++) {
                sum += mBuffer[i] * mBuffer[i];
//            sum += Math.abs(mBuffer[i]);
            }

            if (bufferReadSize > 0) {
                sum /= bufferReadSize;
                return (int) Math.sqrt(sum);
//            return sum / bufferReadSize;
            }

            return 0;
        }

        // https://github.com/poga/GiderosMicBlow/blob/master/Android/AudioMeter.java
        public int getMaxRawAmplitude() {
            if (mAudioRecord == null) {
                createAudioRecord();
            }

            final int bufferReadSize = mAudioRecord.read(mBuffer, 0, mBufferSize);

            if (bufferReadSize < 0) {
                return 0;
            }

            int maxVolume = 0;
            for (int i = 0; i < bufferReadSize; i++) {
                if (Math.abs(mBuffer[i]) > maxVolume) {
                    maxVolume = Math.abs(mBuffer[i]);
                }
            }

            return maxVolume;
        }
    }
}