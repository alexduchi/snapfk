package com.neo.autosub.localfull;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class AudioDecoder {
    static float[] decode16kMono(Context context, Uri uri) throws Exception {
        MediaExtractor ex = new MediaExtractor();
        ex.setDataSource(context, uri, null);
        int track = -1;
        MediaFormat inputFormat = null;
        for (int i=0;i<ex.getTrackCount();i++) {
            MediaFormat f=ex.getTrackFormat(i);
            String mime=f.getString(MediaFormat.KEY_MIME);
            if (mime!=null && mime.startsWith("audio/")) { track=i; inputFormat=f; break; }
        }
        if(track<0) throw new IllegalArgumentException("Aucune piste audio dans cette vidéo");
        ex.selectTrack(track);
        String mime=inputFormat.getString(MediaFormat.KEY_MIME);
        MediaCodec codec=MediaCodec.createDecoderByType(mime);
        codec.configure(inputFormat,null,null,0);
        codec.start();
        boolean inputDone=false, outputDone=false;
        MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
        ByteArrayOutputStream pcm=new ByteArrayOutputStream();
        int sampleRate=inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)?inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE):44100;
        int channels=inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)?inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT):2;
        while(!outputDone){
            if(!inputDone){
                int inIndex=codec.dequeueInputBuffer(10000);
                if(inIndex>=0){
                    ByteBuffer in=codec.getInputBuffer(inIndex);
                    int size=ex.readSampleData(in,0);
                    if(size<0){ codec.queueInputBuffer(inIndex,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone=true; }
                    else { codec.queueInputBuffer(inIndex,0,size,ex.getSampleTime(),0); ex.advance(); }
                }
            }
            int outIndex=codec.dequeueOutputBuffer(info,10000);
            if(outIndex==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                MediaFormat of=codec.getOutputFormat();
                if(of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate=of.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                if(of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels=of.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            } else if(outIndex>=0){
                ByteBuffer out=codec.getOutputBuffer(outIndex);
                if(out!=null && info.size>0){
                    byte[] b=new byte[info.size]; out.position(info.offset); out.limit(info.offset+info.size); out.get(b); pcm.write(b);
                }
                outputDone=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                codec.releaseOutputBuffer(outIndex,false);
            }
        }
        codec.stop(); codec.release(); ex.release();
        byte[] bytes=pcm.toByteArray();
        int frames=bytes.length/(2*Math.max(1,channels));
        float[] mono=new float[frames];
        ByteBuffer bb=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        for(int i=0;i<frames;i++){
            float sum=0;
            for(int ch=0;ch<channels;ch++) sum += bb.getShort()/32768f;
            mono[i]=sum/channels;
        }
        if(sampleRate==16000) return mono;
        int outN=(int)Math.max(1,Math.round(mono.length*(16000.0/sampleRate)));
        float[] res=new float[outN];
        double ratio=sampleRate/16000.0;
        for(int i=0;i<outN;i++){
            double src=i*ratio; int a=(int)src; int b=Math.min(a+1,mono.length-1); float t=(float)(src-a);
            res[i]=mono[a]*(1-t)+mono[b]*t;
        }
        return res;
    }
}
