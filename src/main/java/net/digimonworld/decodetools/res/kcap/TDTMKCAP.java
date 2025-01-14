package net.digimonworld.decodetools.res.kcap;

import static de.javagl.jgltf.model.GltfConstants.GL_ARRAY_BUFFER;
import static de.javagl.jgltf.model.GltfConstants.GL_FLOAT;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Hashtable;
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

    public void exportGLTFAnimation(GlTF instance, int index) {
        List<AnimationChannel> channels = new ArrayList<AnimationChannel>();
        List<AnimationSampler> samplers = new ArrayList<AnimationSampler>();

        String name;

        switch (index) {
            case 0: name = "idle"; break;
            case 1: name = "run"; break;
            case 2: name = "happy"; break;
            case 3: name = "refuse"; break;
            case 4: name = "sleep"; break;
            case 5: name = "exhausted"; break;
            case 6: name = "attack"; break;
            case 7: name = "attack2"; break;
            case 9: name = "guard"; break;
            case 10: name = "hit"; break;
            case 11: name = "defeated"; break;
            case 12: name = "specialattack"; break;

            default: name = "anim_" + index; break;
        }

        System.out.println(name);

        // Each TDTM Entry can only map one joint, contains translation OR rotation OR scale
        for (int i = 0; i < tdtmEntry.size(); i++) {
            TDTMEntry tEntry = tdtmEntry.get(i);
            int jointId = tEntry.jointId;
            float animDuration = (time2-time1)/300;

            System.out.println("TDTM Entry " + i + ": Joint " + jointId + ", Mode " + tEntry.mode);

            // Create an animation channel target
            AnimationChannelTarget act = new AnimationChannelTarget();
            act.setNode(jointId); // Set Node

            // Linear 1D by default
            InterpolationMode interMode = InterpolationMode.LINEAR_1D;

            // Every 30 Frames by default
            TimeScale timeScale = TimeScale.EVERY_30_FRAMES;

            float xMin = Float.POSITIVE_INFINITY;
            float xMax = Float.NEGATIVE_INFINITY;
            float yMin = Float.POSITIVE_INFINITY;
            float yMax = Float.NEGATIVE_INFINITY;
            float zMin = Float.POSITIVE_INFINITY;
            float zMax = Float.NEGATIVE_INFINITY;
            float wMin = Float.POSITIVE_INFINITY;
            float wMax = Float.NEGATIVE_INFINITY;

            boolean has00data = false;
            boolean has02data = false;

            float[] qstm00data = { 0, 0, 0, 0 };

            List<Float> times = new ArrayList<Float>();

            Hashtable<Float, Float> xValues = new Hashtable<Float, Float>();
            Hashtable<Float, Float> yValues = new Hashtable<Float, Float>();
            Hashtable<Float, Float> zValues = new Hashtable<Float, Float>();
            Hashtable<Float, Float> wValues = new Hashtable<Float, Float>();

            QSTMPayload qstmPayload = qstm.get(tEntry.qstmId);

            for (int j = 0; j < qstmPayload.getEntries().size(); j++) {
                QSTMEntry qEntry = qstmPayload.getEntries().get(j);
                QSTMEntryType type = qEntry.getType();

                Axis axis = Axis.NONE;

                //System.out.print("QSTM " + j + " [" + qEntry.getType() + "]: ");

                switch(type.getId()) {
                    case 0: // QSTM00Entry, for static values or 
                        axis = ((QSTM00Entry)qEntry).getAxis();
                        has00data = true;

                        List<Float> qstm0Values = ((QSTM00Entry)qEntry).getValues();

                        // for (int k = 0; k < qstm0Values.size(); k++) {
                        //     System.out.print(qstm0Values.get(k) + ", ");
                        // }

                        if (qstm0Values.size() == 3) {
                            qstm00data[0] = qstm0Values.get(0);
                            qstm00data[1] = qstm0Values.get(1);
                            qstm00data[2] = qstm0Values.get(2);
                        }
                        else {
                            switch(axis) {
                                case X: qstm00data[0] = qstm0Values.get(0); break;
                                case Y: qstm00data[1] = qstm0Values.get(0); break;
                                case Z: qstm00data[2] = qstm0Values.get(0); break;
                                case W: qstm00data[3] = qstm0Values.get(0); break;
                                case NONE: break;
                            }
                        }
                        break;
                    case 1: // QSTM01Entry, don't know how to do this yet
                        System.out.println("QSTM type 01");
                        break;
                    case 2: // QSTM02Entry (has to access VCTM)
                        axis = ((QSTM02Entry)qEntry).getAxis();
                        has02data = true;

                        VCTMPayload vctmPayload = vctm.get(((QSTM02Entry)qEntry).getVctmId());

                        interMode = vctmPayload.getInterpolationMode();
                        timeScale = vctmPayload.getTimeScale();

                        float[] qstmTimes = vctmPayload.getFrameList();
                        float[][] qstmValues = vctmPayload.getFrameDataList();

                        // Convert frames to seconds
                        float[] timestamps = new float[qstmTimes.length];
                        
                        for (int k = 0; k < qstmTimes.length; k++) {
                            timestamps[k] = (animDuration)*(qstmTimes[k])/qstmTimes[qstmTimes.length-1];
                        }

                        for (int k = 0; k < timestamps.length; k++) {
                            if (!times.contains(timestamps[k])) {
                                times.add(timestamps[k]);
                            }

                            if (qstmValues[0].length > 1) {
                                for (int l = 0; l < qstmValues[0].length; l++) {
                                    switch(l) {
                                        case 0: xValues.put(timestamps[k], qstmValues[k][l]); break;
                                        case 1: yValues.put(timestamps[k], qstmValues[k][l]); break;
                                        case 2: zValues.put(timestamps[k], qstmValues[k][l]); break;
                                        case 3: wValues.put(timestamps[k], qstmValues[k][l]); break;
                                        default: break;
                                    }
                                }
                            }
                            else {
                                switch(axis) {
                                    case X: xValues.put(timestamps[k], qstmValues[k][0]); break;
                                    case Y: yValues.put(timestamps[k], qstmValues[k][0]); break;
                                    case Z: zValues.put(timestamps[k], qstmValues[k][0]); break;
                                    case W: wValues.put(timestamps[k], qstmValues[k][0]); break;
                                    default: break;
                                }
                            }
                        }

                        Collections.sort(times);
                        break;
                }

                //System.out.println();
            }

            if (has00data && !has02data) {
                times.add((float)0.0);
                xValues.put((float)0.0, qstm00data[0]);
                yValues.put((float)0.0, qstm00data[1]);
                zValues.put((float)0.0, qstm00data[2]);
                wValues.put((float)0.0, qstm00data[3]);
            }

            //System.out.println("Offset: " + offset[0] + ", " + offset[1] + ", " + offset[2] + ", " + offset[3]);

            float[] posTimes = new float[times.size()];
            float[][] posValues = new float[times.size()][3];

            float[] rotTimes = new float[times.size()];
            float[][] rotValues = new float[times.size()][4];

            float[] scaTimes = new float[times.size()];
            float[][] scaValues = new float[times.size()][3];

            float x, y, z, w;

            float timeMin = Float.POSITIVE_INFINITY;
            float timeMax = Float.NEGATIVE_INFINITY;

            for (int k = 0; k < times.size(); k++) {
                if (tEntry.mode == TDTMMode.TRANSLATION) {
                    posTimes[k] = times.get(k);

                    timeMin = Math.min(timeMin, posTimes[k]);
                    timeMax = Math.max(timeMax, posTimes[k]);

                    x = xValues.containsKey(times.get(k)) ? xValues.get(times.get(k)) + qstm00data[0] : 0;
                    y = yValues.containsKey(times.get(k)) ? yValues.get(times.get(k)) + qstm00data[1] : 0;
                    z = zValues.containsKey(times.get(k)) ? zValues.get(times.get(k)) + qstm00data[2] : 0;

                    xMin = Math.min(xMin, x);
                    xMax = Math.max(xMax, x);

                    yMin = Math.min(yMin, y);
                    yMax = Math.max(yMax, y);

                    zMin = Math.min(zMin, z);
                    zMax = Math.max(zMax, z);

                    posValues[k][0] = x;
                    posValues[k][1] = y;
                    posValues[k][2] = z;
                }
                else if (tEntry.mode == TDTMMode.ROTATION) {
                    rotTimes[k] = times.get(k);

                    timeMin = Math.min(timeMin, rotTimes[k]);
                    timeMax = Math.max(timeMax, rotTimes[k]);

                    x = xValues.containsKey(times.get(k)) ? xValues.get(times.get(k)) + qstm00data[0] : 0;
                    y = yValues.containsKey(times.get(k)) ? yValues.get(times.get(k)) + qstm00data[1] : 0;
                    z = zValues.containsKey(times.get(k)) ? zValues.get(times.get(k)) + qstm00data[2] : 0;
                    w = wValues.containsKey(times.get(k)) ? wValues.get(times.get(k)) + qstm00data[3] : 0;

                    xMin = Math.min(xMin, x);
                    xMax = Math.max(xMax, x);

                    yMin = Math.min(yMin, y);
                    yMax = Math.max(yMax, y);

                    zMin = Math.min(zMin, z);
                    zMax = Math.max(zMax, z);

                    wMin = Math.min(wMin, w);
                    wMax = Math.max(wMax, w);

                    rotValues[k][0] = x;
                    rotValues[k][1] = y;
                    rotValues[k][2] = z;
                    rotValues[k][3] = w;
                }
                else { // SCALE or LOCAL_SCALE
                    scaTimes[k] = times.get(k);

                    timeMin = Math.min(timeMin, scaTimes[k]);
                    timeMax = Math.max(timeMax, scaTimes[k]);

                    x = xValues.containsKey(times.get(k)) ? xValues.get(times.get(k)) + qstm00data[0] : 1;
                    y = yValues.containsKey(times.get(k)) ? yValues.get(times.get(k)) + qstm00data[1] : 1;
                    z = zValues.containsKey(times.get(k)) ? zValues.get(times.get(k)) + qstm00data[2] : 1;

                    xMin = Math.min(xMin, x);
                    xMax = Math.max(xMax, x);

                    yMin = Math.min(yMin, y);
                    yMax = Math.max(yMax, y);

                    zMin = Math.min(zMin, z);
                    zMax = Math.max(zMax, z);
                    
                    scaValues[k][0] = x;
                    scaValues[k][1] = y;
                    scaValues[k][2] = z;
                }
            }

            AnimationSampler as = new AnimationSampler();
            as.setInterpolation("LINEAR");

            int timeBuffer, timeBufferView, timeAccessor = 0;
            int valBuffer, valBufferView, valAccessor = 0;

            // System.out.println(tEntry.mode);

            if (tEntry.mode == TDTMMode.TRANSLATION) {
                act.setPath("translation");

                // System.out.println("time | x | y | z");

                // for (int k = 0; k < posTimes.length; k++) {
                //     System.out.println(posTimes[k] + " | " + posValues[k][0] + " | " + posValues[k][1] + " | " + posValues[k][2]);
                // }

                Number[] posInMin = { timeMin };
                Number[] posInMax = { timeMax };

                Number[] posOutMin = { xMin, yMin, zMin };
                Number[] posOutMax = { xMax, yMax, zMax };

                timeBuffer = arrayToBuffer(posTimes, instance);
                timeBufferView = createBufferView(timeBuffer, 0, "posTimeBufferView_"+i, instance);
                timeAccessor = createAccessor(timeBufferView, GL_FLOAT, posTimes.length, "SCALAR", "posTimeAccessor_"+i, posInMax, posInMin, instance);

                valBuffer = vectorToBuffer(posValues, instance);
                valBufferView = createBufferView(valBuffer, 0, "posValueBufferView_"+i, instance);
                valAccessor = createAccessor(valBufferView, GL_FLOAT, posValues.length, "VEC3", "posValueAccessor_"+i, posOutMax, posOutMin, instance);

                as.setInput(timeAccessor);
                as.setOutput(valAccessor);
            }
            else if (tEntry.mode == TDTMMode.ROTATION) {
                act.setPath("rotation");

                // System.out.println("time | x | y | z | w");

                // for (int k = 0; k < rotTimes.length; k++) {
                //     System.out.println(rotTimes[k] + " | " + rotValues[k][0] + " | " + rotValues[k][1] + " | " + rotValues[k][2] + " | " + rotValues[k][3]);
                // }

                Number[] rotInMin = { timeMin };
                Number[] rotInMax = { timeMax };

                Number[] rotOutMin = { xMin, yMin, zMin, wMin };
                Number[] rotOutMax = { xMax, yMax, zMax, wMax };

                timeBuffer = arrayToBuffer(rotTimes, instance);
                timeBufferView = createBufferView(timeBuffer, 0, "rotTimeBufferView_"+i, instance);
                timeAccessor = createAccessor(timeBufferView, GL_FLOAT, rotTimes.length, "SCALAR", "rotTimeAccessor_"+i, rotInMax, rotInMin, instance);

                valBuffer = vectorToBuffer(rotValues, instance);
                valBufferView = createBufferView(valBuffer, 0, "rotValueBufferView_"+i, instance);
                valAccessor = createAccessor(valBufferView, GL_FLOAT, rotValues.length, "VEC4", "rotValueAccessor_"+i, rotOutMax, rotOutMin, instance);

                as.setInput(timeAccessor);
                as.setOutput(valAccessor);
            }
            else {
                act.setPath("scale");

                // System.out.println("time | x | y | z");

                // for (int k = 0; k < scaTimes.length; k++) {
                //     System.out.println(scaTimes[k] + " | " + scaValues[k][0] + " | " + scaValues[k][1] + " | " + scaValues[k][2]);
                // }

                Number[] scaInMin = { timeMin };
                Number[] scaInMax = { timeMax };

                Number[] scaOutMin = { xMin, yMin, zMin };
                Number[] scaOutMax = { xMax, yMax, zMax };

                timeBuffer = arrayToBuffer(scaTimes, instance);
                timeBufferView = createBufferView(timeBuffer, 0, "scaTimeBufferView_"+i, instance);
                timeAccessor = createAccessor(timeBufferView, GL_FLOAT, scaTimes.length, "SCALAR", "scaTimeAccessor_"+i, scaInMax, scaInMin, instance);

                valBuffer = vectorToBuffer(scaValues, instance);
                valBufferView = createBufferView(valBuffer, 0, "scaValueBufferView_"+i, instance);
                valAccessor = createAccessor(valBufferView, GL_FLOAT, scaValues.length, "VEC3", "scaValueAccessor_"+i, scaOutMax, scaOutMin, instance);

                as.setInput(timeAccessor);
                as.setOutput(valAccessor);
            }

            samplers.add(as);

            AnimationChannel ac = new AnimationChannel();
            ac.setTarget(act);
            ac.setSampler(samplers.size() - 1);
            
            channels.add(ac);
        }

        Animation anim = new Animation();

        anim.setName(name);
        anim.setChannels(channels);
        anim.setSamplers(samplers);

        instance.addAnimations(anim);
    }

    private int arrayToBuffer(float[] arr, GlTF instance) {
        ByteBuffer buffer = ByteBuffer.allocate(arr.length*4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        for (float x : arr)
            buffer.putFloat(x);
        
        buffer.flip();

        Buffer gltfBuffer = new Buffer();
        gltfBuffer.setUri(GLTFExporter.BUFFER_URI + Base64.getEncoder().encodeToString(buffer.array()));
        gltfBuffer.setByteLength(arr.length*4);
        instance.addBuffers(gltfBuffer);

        return instance.getBuffers().size() - 1;
    }

    private int vectorToBuffer(float[][] vec, GlTF instance) {
        ByteBuffer buffer = ByteBuffer.allocate(vec.length*vec[0].length*4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < vec.length; i++) {
            for (int j = 0; j < vec[0].length; j++) {
                buffer.putFloat(vec[i][j]);
            }
        }

        buffer.flip();

        Buffer gltfBuffer = new Buffer();
        gltfBuffer.setUri(GLTFExporter.BUFFER_URI + Base64.getEncoder().encodeToString(buffer.array()));
        gltfBuffer.setByteLength(vec.length*vec[0].length*4);
        instance.addBuffers(gltfBuffer);

        return instance.getBuffers().size() - 1;
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

    private int createAccessor(int bufferView, int componentType, int count, String type, String name, Number[] max, Number[] min, GlTF instance) {
        Accessor accessor = new Accessor();
        accessor.setBufferView(bufferView);
        accessor.setComponentType(componentType);
        accessor.setCount(count);
        accessor.setType(type);
        accessor.setName(name);
        accessor.setMax(max);
        accessor.setMin(min);
        instance.addAccessors(accessor);
        return instance.getAccessors().size() - 1;
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
