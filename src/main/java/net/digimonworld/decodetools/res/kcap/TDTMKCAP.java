package net.digimonworld.decodetools.res.kcap;

import static de.javagl.jgltf.model.GltfConstants.GL_ARRAY_BUFFER;
import static de.javagl.jgltf.model.GltfConstants.GL_FLOAT;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

import de.javagl.jgltf.impl.v2.Accessor;
import de.javagl.jgltf.impl.v2.Animation;
import de.javagl.jgltf.impl.v2.AnimationChannel;
import de.javagl.jgltf.impl.v2.AnimationChannelTarget;
import de.javagl.jgltf.impl.v2.AnimationSampler;
import de.javagl.jgltf.impl.v2.Buffer;
import de.javagl.jgltf.impl.v2.BufferView;
import de.javagl.jgltf.impl.v2.GlTF;
import de.javagl.jgltf.impl.v2.Sampler;
import net.digimonworld.decodetools.Main;
import net.digimonworld.decodetools.core.Access;
import net.digimonworld.decodetools.core.Utils;
import net.digimonworld.decodetools.gui.GLTFExporter;
import net.digimonworld.decodetools.res.ResData;
import net.digimonworld.decodetools.res.ResPayload;
import net.digimonworld.decodetools.res.payload.QSTMPayload;
import net.digimonworld.decodetools.res.payload.VCTMPayload;
import net.digimonworld.decodetools.res.payload.VCTMPayload.InterpolationMode;
import net.digimonworld.decodetools.res.payload.VCTMPayload.TimeScale;
import net.digimonworld.decodetools.res.payload.qstm.Axis;
import net.digimonworld.decodetools.res.payload.qstm.QSTM00Entry;
import net.digimonworld.decodetools.res.payload.qstm.QSTM02Entry;
import net.digimonworld.decodetools.res.payload.qstm.QSTMEntry;
import net.digimonworld.decodetools.res.payload.qstm.QSTMEntryType;

//TODO figure out how TDTM works and abstrahize it per entry
// sets up the animation data, i.e. initial positions, which anim data to use and more
public class TDTMKCAP extends AbstractKCAP {
    private static final int TDTM_VERSION = 2;
    
    private List<TDTMEntry> tdtmEntry = new ArrayList<>();
    
    private List<QSTMPayload> qstm = new ArrayList<>();
    private List<VCTMPayload> vctm = new ArrayList<>();
    
    private float time1; // start
    private float time2; // end
    private float time3; // loop start
    private float time4; // loop end
    
    protected TDTMKCAP(AbstractKCAP parent, Access source, int dataStart, KCAPInformation info) {
        super(parent, info.flags);
        
        // make sure it's actually a TDTM
        if (source.readInteger() != KCAPType.TDTM.getMagicValue())
            throw new IllegalArgumentException("Tried to instanciate TDTM KCAP, but didn't find a TDTM header.");
        
        int version = source.readInteger();
        if (version != TDTM_VERSION)
            throw new IllegalArgumentException("Tried to instanciate TDTM KCAP and expected version 2, but got " + version);
        
        int numEntries = source.readInteger();
        source.readInteger(); // padding
        
        time1 = source.readFloat();
        time2 = source.readFloat();
        time3 = source.readFloat();
        time4 = source.readFloat();
        
        // one TDTM entry per QSTM in qstmKCAP?
        for (int i = 0; i < numEntries; ++i)
            tdtmEntry.add(new TDTMEntry(source));
        
        // TODO inconsistent by design?
        if (numEntries % 2 == 1 && info.headerSize % 0x10 == 0x00)
            source.readLong(); // padding
            
        List<KCAPPointer> pointer = loadKCAPPointer(source, info.entries);
        
        if (pointer.size() != 2)
            throw new IllegalArgumentException("A TDTM KCAP always has two elements, but this one has " + pointer.size() + "!");
        
        source.setPosition(info.startAddress + pointer.get(0).getOffset());
        NormalKCAP qstmKCAP = (NormalKCAP) AbstractKCAP.craftKCAP(source, this, dataStart);
        
        source.setPosition(info.startAddress + pointer.get(1).getOffset());
        NormalKCAP vctmKCAP = (NormalKCAP) AbstractKCAP.craftKCAP(source, this, dataStart);
        
        for (ResPayload entry : qstmKCAP) {
            if (entry.getType() != Payload.QSTM)
                throw new IllegalArgumentException("Got a " + entry.getType() + " entry, but only QSTM entries are allowed.");
            
            qstm.add((QSTMPayload) entry);
        }
        
        for (ResPayload entry : vctmKCAP) {
            if (entry.getType() != Payload.VCTM)
                throw new IllegalArgumentException("Got a " + entry.getType() + " entry, but only VCTM entries are allowed.");
            
            vctm.add((VCTMPayload) entry);
        }
        
        // make sure we're at the end of the KCAP
        long expectedEnd = info.startAddress + info.size;
        if (source.getPosition() != expectedEnd)
            Main.LOGGER.warning(() -> "Final position for TDTM KCAP does not match the header. Current: " + source.getPosition() + " Expected: " + expectedEnd);
    }

    // each animation is in a TDTM
    // access it from parent of HSMP passed to gltf exporter
    // The TDTM contains start and end times of the animation, says whether it’s editing joints, material or texture
    // within the TDTM, there are paired QSTM and VCTM files
    // Each QSTM describes how a joint is changed
    // Each VCTM has a series of keyframe vectors for that transformation, with time related data
    // In gltf exporter:
    // get all tdtms
    // for each tdtm
    // check that it is a rig animation
    // get start and end times
    // get QSTMs and VCTMs
    // for each QSTM and VCTM
    // use QSTM to determine joint and translation/rotation/scale
    // use VCTM to get the keyframes (should have time scale)
    // create an animation from the extracted data
    // add to model
    // use chair animation as reference to determine how data is stored
    // model import (ideal)
    // convert fbx and sprite sheet to res, specify which animation is which


    public void exportGLTFAnimation(GlTF instance) {
        
        // gltf animation needs:
        //      channels (combines a sampler with a target)
        //      samplers (combines a timestamp with output values and interpolation)
        //  So we need the joint, the timestamp, the values for each axis

        List<AnimationChannel> channels = new ArrayList<>();
        List<AnimationSampler> samplers = new ArrayList<>();

        // Each TDTM Entry can only map one joint
        for (int i = 0; i < tdtmEntry.size(); i++) {
            TDTMEntry tEntry = tdtmEntry.get(i);
            int jointId = tEntry.jointId;

            // Create an animation channel target
            AnimationChannelTarget act = new AnimationChannelTarget();
            act.setNode(jointId); // Set Node

            float[] x_times = new float[0];
            float[] x_values = new float[0];

            float[] y_times =  new float[0];
            float[] y_values = new float[0];

            float[] z_times = new float[0];
            float[] z_values = new float[0];

            float[] w_times = new float[0];
            float[] w_values = new float[0];

            // Linear 1D by default
            InterpolationMode interMode = InterpolationMode.LINEAR_1D;

            // Every 30 Frames by default
            TimeScale timeScale = TimeScale.EVERY_30_FRAMES;

            System.out.println("TDTM Entry " + i);

            QSTMPayload qstmPayload = qstm.get(tEntry.qstmId);

            for (int j = 0; j < qstmPayload.getEntries().size(); j++) {
                QSTMEntry qEntry = qstmPayload.getEntries().get(j);
                QSTMEntryType type = qEntry.getType();

                Axis axis = Axis.NONE;

                float[] frames = new float[0];
                float[] frameData = new float[0];

                switch(type.getId()) {
                    case 0: // QSTM00Entry, only 1 or 3 
                        axis = ((QSTM00Entry)qEntry).getAxis();
                        List<Float> values = ((QSTM00Entry)qEntry).getValues();

                        frames = new float[values.size()];
                        frameData = new float[values.size()];

                        for (int k = 0; k < values.size(); k++) {
                            frames[k] = k;
                            frameData[k] = values.get(k);
                        }

                        break;
                    case 1: // QSTM01Entry, don't know how to do this yet
                        break;
                    case 2: // QSTM02Entry (has to access VCTM)
                        axis = ((QSTM02Entry)qEntry).getAxis();
                        VCTMPayload vctmPayload = vctm.get(((QSTM02Entry)qEntry).getVctmId());

                        interMode = vctmPayload.getInterpolationMode();

                        timeScale = vctmPayload.getTimeScale();

                        frames = vctmPayload.getFrameList();
                        frameData = vctmPayload.getFrameDataList();
                        break;
                }
                
                // Convert frames to seconds
                float animDuration = time2-time1;
                float[] timestamps = new float[frames.length];
                
                for (int k = 0; k < frames.length; k++) {
                    timestamps[k] = (float) (animDuration * frames[k])/frames[frames.length-1];
                }

                switch(axis) {
                    case X:
                        x_times = timestamps;
                        x_values = frameData;
                        break;
                    case Y:
                        y_times = timestamps;
                        y_values = frameData;
                        break;
                    case Z:
                        z_times = timestamps;
                        z_values = frameData;
                        break;
                    case W:
                        w_times = timestamps;
                        w_values = frameData;
                        break;
                    default:
                        x_times = timestamps;
                        x_values = frameData;
                        break;
                }

                System.out.println("Joint: " + jointId + ", Duration: " + animDuration);
                System.out.println("Axis: " + axis + ", Interpolation Mode: " + interMode + ", Time Scale: " + timeScale);

                System.out.println("QSTM " + j + ": " + qEntry.getType());

                for (int k = 0; k < frames.length; k++) {
                    System.out.println(timestamps[k] + ": " + frameData[k]);
                }
                
            }

            // Create a buffer and accessor with the time stamps
            int timeBuffer = arrayToBuffer(x_times, instance);
            int timeBufferView = createBufferView(timeBuffer, GL_ARRAY_BUFFER, "timeBV_"+i, instance);
            int timeAccessor = createAccessor(timeBufferView, GL_FLOAT, x_times.length, "SCALAR", "timeAccessor_"+i, instance);

            // Set them to be the input and output of the animation sampler
            AnimationSampler as = new AnimationSampler();
            as.setInterpolation("LINEAR");
            as.setInput(timeAccessor);

            int valBuffer;
            int valBufferView;
            int valAccessor;
            float[][] vector = new float[3][];
            int maxSize = 0;

            maxSize = Math.max(x_values.length, y_values.length);
            maxSize = Math.max(maxSize, z_values.length);
            maxSize = Math.max(maxSize, w_values.length);

            float[] new_x_values = new float[maxSize];
            float[] new_y_values = new float[maxSize];
            float[] new_z_values = new float[maxSize];
            float[] new_w_values = new float[maxSize];

            for (int j = 0; j < maxSize; j++) {
                if (j < x_values.length) {
                    new_x_values[j] = x_values[j];
                }
                else {
                    new_x_values[j] = 0;
                }

                if (j < y_values.length) {
                    new_y_values[j] = y_values[j];
                }
                else {
                    new_y_values[j] = 0;
                }

                if (j < z_values.length) {
                    new_z_values[j] = z_values[j];
                }
                else {
                    new_z_values[j] = 0;
                }

                if (j < w_values.length) {
                    new_w_values[j] = w_values[j];
                }
                else {
                    new_w_values[j] = 0;
                }
            }

            switch(tEntry.mode) {
                case TRANSLATION:
                    act.setPath("translation");

                    vector[0] = new_x_values;
                    vector[1] = new_y_values;
                    vector[2] = new_z_values;

                    // Create a buffer and accessor with the values
                    valBuffer = vectorToBuffer(vector, instance);
                    valBufferView = createBufferView(valBuffer, GL_ARRAY_BUFFER, "valueBV_"+i, instance);
                    valAccessor = createAccessor(valBufferView, GL_FLOAT, vector.length, "VEC3", "valueAccessor_"+i, instance);
                    
                    as.setOutput(valAccessor);
                    break;
                case ROTATION:
                    act.setPath("rotation");

                    vector = new float[4][];
                    vector[0] = new_x_values;
                    vector[1] = new_y_values;
                    vector[2] = new_z_values;
                    vector[3] = new_w_values;

                    // Create a buffer and accessor with the values
                    valBuffer = vectorToBuffer(vector, instance);
                    valBufferView = createBufferView(valBuffer, GL_ARRAY_BUFFER, "valueBV_"+i, instance);
                    valAccessor = createAccessor(valBufferView, GL_FLOAT, vector.length, "VEC4", "valueAccessor_"+i, instance);
                    
                    as.setOutput(valAccessor);
                    break;
                case SCALE:
                case LOCAL_SCALE:
                    act.setPath("scale");

                    vector[0] = new_x_values;
                    vector[1] = new_y_values;
                    vector[2] = new_z_values;

                    // Create a buffer and accessor with the values
                    valBuffer = vectorToBuffer(vector, instance);
                    valBufferView = createBufferView(valBuffer, GL_ARRAY_BUFFER, "valueBV_"+i, instance);
                    valAccessor = createAccessor(valBufferView, GL_FLOAT, vector.length, "VEC3", "valueAccessor_"+i, instance);
                    
                    as.setOutput(valAccessor);
                    break;
            }

            Sampler sampler = new Sampler();
            instance.addSamplers(sampler);
            int samplerIndex = instance.getSamplers().size() - 1;

            AnimationChannel ac = new AnimationChannel();
            ac.setTarget(act);
            ac.setSampler(samplerIndex);
        }

        Animation anim = new Animation();

        // Error: number of channel elements is < 1 ?
        anim.setChannels(channels);
        anim.setSamplers(samplers);

        instance.addAnimations(anim);
    }

    private int arrayToBuffer(float[] arr, GlTF instance) {
        ByteBuffer mBuffer = ByteBuffer.allocate(arr.length*4);
        mBuffer.order(ByteOrder.LITTLE_ENDIAN);

        for (float x : arr)
                mBuffer.putFloat(x);

        //mBuffer.flip();

        Buffer gltfBuffer = new Buffer();
        gltfBuffer.setUri(GLTFExporter.BUFFER_URI + Base64.getEncoder().encodeToString(mBuffer.array()));
        gltfBuffer.setByteLength(arr.length*4);
        instance.addBuffers(gltfBuffer);

        return instance.getBuffers().size() - 1;
    }

    private int vectorToBuffer(float[][] vec, GlTF instance) {
        ByteBuffer mBuffer = ByteBuffer.allocate(vec.length*vec[0].length*4);
        mBuffer.order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < vec.length; i++) {
            for (int j = 0; j < vec[0].length; j++) {
                mBuffer.putFloat(vec[i][j]);
            }
        }
        //mBuffer.flip();

        Buffer gltfBuffer = new Buffer();
        gltfBuffer.setUri(GLTFExporter.BUFFER_URI + Base64.getEncoder().encodeToString(mBuffer.array()));
        gltfBuffer.setByteLength(vec.length*vec[0].length*4);
        instance.addBuffers(gltfBuffer);

        return instance.getBuffers().size() - 1;
    }

    private int createAccessor(int bufferView, int componentType, int count, String type, String name, GlTF instance) {
        Accessor accessor = new Accessor();
        accessor.setBufferView(bufferView);
        accessor.setComponentType(componentType);
        accessor.setCount(count);
        accessor.setType(type);
        accessor.setName(name);
        instance.addAccessors(accessor);
        return instance.getAccessors().size() - 1;
    }

    private int createBufferView(int buffer, int target, String name, GlTF instance) {
        BufferView bufferView = new BufferView();
        bufferView.setBuffer(buffer);
        bufferView.setByteOffset(0);
        bufferView.setByteLength(instance.getBuffers().get(buffer).getByteLength());
        if (target != 0)
            bufferView.setTarget(target);
        bufferView.setName(name);
        instance.addBufferViews(bufferView);
        return instance.getBufferViews().size() - 1;
    }
    
    @Override
    public List<ResPayload> getEntries() {
        List<ResPayload> entries = new ArrayList<>();
        entries.add(new NormalKCAP(this, qstm, true, false));
        entries.add(new NormalKCAP(this, vctm, true, false));
        
        return Collections.unmodifiableList(entries);
    }
    
    @Override
    public ResPayload get(int i) {
        switch (i) {
            case 0:
                return new NormalKCAP(this, qstm, true, false);
            case 1:
                return new NormalKCAP(this, vctm, true, false);
            default:
                throw new NoSuchElementException();
        }
    }
    
    @Override
    public int getEntryCount() {
        return 2;
    }
    
    @Override
    public KCAPType getKCAPType() {
        return KCAPType.TDTM;
    }
    
    @Override
    public int getSize() {
        int size = 0x40; // header
        size += tdtmEntry.size() * 0x08; // TDTM header entries
        size = Utils.align(size, 0x10); // padding
        size += getEntryCount() * 8; // pointer table
        
        size += get(0).getSize();
        size = Utils.align(size, 0x10); // padding
        size += get(1).getSize();
        
        return size;
    }
    
    @Override
    public void writeKCAP(Access dest, ResData dataStream) {
        long start = dest.getPosition();
        
        int headerSize = 0x40; // header
        headerSize += tdtmEntry.size() * 0x08; // TDTM header entries
        headerSize = Utils.align(headerSize, 0x10); // padding
        
        // write KCAP/TDTM header
        dest.writeInteger(getType().getMagicValue());
        dest.writeInteger(VERSION);
        dest.writeInteger(getSize());
        dest.writeInteger(getUnknown());
        
        dest.writeInteger(getEntryCount());
        dest.writeInteger(0); // type count
        dest.writeInteger(headerSize); // header size, always 0x30 for this type
        dest.writeInteger(0); // type payload start, after the pointer table or 0 if empty
        
        dest.writeInteger(getKCAPType().getMagicValue());
        dest.writeInteger(TDTM_VERSION);
        dest.writeInteger(tdtmEntry.size());
        dest.writeInteger(0); // padding
        
        dest.writeFloat(time1);
        dest.writeFloat(time2);
        dest.writeFloat(time3);
        dest.writeFloat(time4);
        
        // write TDTM entries
        tdtmEntry.forEach(a -> a.writeKCAP(dest));
        
        if (tdtmEntry.size() % 2 == 1)
            dest.writeLong(0); // padding
            
        // write pointer table
        int fileStart = (int) (dest.getPosition() - start + 0x10);
        
        ResPayload qstmKCAP = get(0);
        dest.writeInteger(fileStart);
        dest.writeInteger(qstmKCAP.getSize());
        fileStart += qstmKCAP.getSize();
        
        fileStart = Utils.align(fileStart, 0x10); // align content start
        
        ResPayload vctmKCAP = get(1);
        dest.writeInteger(fileStart);
        dest.writeInteger(vctmKCAP.getSize());
        
        // write entries
        try (ResData localDataStream = new ResData(dataStream)) {
            qstmKCAP.writeKCAP(dest, localDataStream);
            
            long aligned = Utils.align(dest.getPosition() - start, 0x10);
            dest.setPosition(start + aligned);
            
            vctmKCAP.writeKCAP(dest, localDataStream);
            dataStream.add(localDataStream);
        }
    }
    
    class TDTMEntry {
        private TDTMMode mode;
        
        /*
         * 0x10 joint    -> 3x translation | 4x rotation | 3x scale | 4x local scale
         * 0x20 texture  -> 2x translation | 1x rotation | 2x scale | none
         * 0x30 material -> 3x color (RGB) | 1x transparency
         */
        private byte unknown2;
        private short jointId;
        private int qstmId;
        
        public TDTMEntry(TDTMMode unknown1, byte unknown2, short jointId, int qstmId) {
            this.mode = unknown1;
            this.unknown2 = unknown2;
            this.jointId = jointId;
            this.qstmId = qstmId;
        }
        
        public TDTMEntry(Access source) {
            this.mode = TDTMMode.values()[source.readByte()];
            this.unknown2 = source.readByte();
            this.jointId = source.readShort();
            this.qstmId = source.readInteger();
        }
        
        public void writeKCAP(Access dest) {
            dest.writeByte((byte) mode.ordinal());
            dest.writeByte(unknown2);
            dest.writeShort(jointId);
            dest.writeInteger(qstmId);
        }
    }
    
    enum TDTMMode {
        TRANSLATION,
        ROTATION,
        SCALE,
        LOCAL_SCALE;
    }
}
