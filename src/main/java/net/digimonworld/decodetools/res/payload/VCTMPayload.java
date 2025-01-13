package net.digimonworld.decodetools.res.payload;

import java.nio.ByteBuffer;

import net.digimonworld.decodetools.core.Access;
import net.digimonworld.decodetools.core.Utils;
import net.digimonworld.decodetools.res.ResData;
import net.digimonworld.decodetools.res.ResPayload;
import net.digimonworld.decodetools.res.kcap.AbstractKCAP;

/*
 * 4-byte       magic value
 * 4-byte       number of entries
 * 4-byte       offset of entries #2
 * 4-byte       offset of entries #1
 * 
 * 4-byte       unknown
 * 2-byte       size of entry #2
 * 2-byte       size of entry #1
 * float        unknown
 * float        unknown
 * 
 * <entries #1> 
 * <entries #2> 
 * 
 * TODO implement proper structure
 */
public class VCTMPayload extends ResPayload {
    private int numEntries;
    private int coordStart;
    private int entriesStart;
    
    private InterpolationMode interpolationMode; // iterpolation type (< 0xC)
    private byte componentCount;
    private ComponentType componentType;
    private TimeScale timeScale;
    private TimeType timeType;
    
    /*
     * Some loop modes?
     * Upper 4 bits for startTime < currentTime && currentTime <= endTime
     */
    private byte unk4;
    
    private short coordSize;
    private short entrySize;
    private float unknown4;
    private float unknown5;
    
    private VCTMEntry[] data1; // frame count
    private VCTMEntry[] data2; // frame data
    
    public VCTMPayload(Access source, int dataStart, AbstractKCAP parent, int size, String name) {
        this(source, dataStart, parent);
    }
    
    public VCTMPayload(Access source, int dataStart, AbstractKCAP parent) {
        super(parent);
        long start = source.getPosition();
        
        source.readInteger(); // magic value
        numEntries = source.readInteger();
        coordStart = source.readInteger();
        entriesStart = source.readInteger();
        
        interpolationMode = InterpolationMode.values()[source.readByte()];
        
        int componentFlags = source.readByte();
        componentCount = (byte) (componentFlags >> 4);
        componentType = ComponentType.values()[componentFlags & 0xF];
        
        int timeFlags = source.readByte();
        timeScale = TimeScale.values()[timeFlags >> 4];
        timeType = TimeType.values()[timeFlags & 0xF];
        
        unk4 = source.readByte();
        
        coordSize = source.readShort();
        entrySize = source.readShort();
        unknown4 = source.readFloat();
        unknown5 = source.readFloat();
        
        data1 = new VCTMEntry[numEntries];
        data2 = new VCTMEntry[numEntries];
        
        source.setPosition(start + entriesStart);
        for (int i = 0; i < numEntries; i++)
            data1[i] = new VCTMEntry(source.readByteArray(entrySize));
        
        source.setPosition(start + coordStart);
        for (int i = 0; i < numEntries; i++)
            data2[i] = new VCTMEntry(source.readByteArray(coordSize));
        
        source.setPosition(Utils.align(source.getPosition(), 0x04));
    }

    public float[] getFrameList() {
        float[] frames = new float[numEntries];

        //System.out.println("Time Type: " + timeType);

        for (int i = 0; i < numEntries; i++) {
            byte[] data = data1[i].getData();

            if (data.length < 4) {
                byte[] newData = new byte[4];
                for (int j = 1; j <= 4; j++) {
                    if (j > data.length) {
                        newData[newData.length-j] = 0x00;
                    }
                    else {
                        newData[newData.length-j] = data[data.length-j]; 
                    }
                }

                data = newData;
            }
            frames[i] = (float)ByteBuffer.wrap(data).getInt();

            // StringBuilder sb = new StringBuilder();
            // for (byte b : data) {
            //     sb.append(String.format("%02X ", b));
            // }
            // System.out.println(sb.toString() + " = " + frames[i]);
        }

        return frames;
    }

    public float[][] getFrameDataList() {
        byte[] compCountData = {0x00, 0x00, 0x00, componentCount};
        int compCount = ByteBuffer.wrap(compCountData).getInt();

        int dataSize = coordSize / compCount;

        float[][] frameData = new float[numEntries][compCount];

        // System.out.println("Component Type: " + componentType);
        // System.out.println("Data size: " + dataSize + " bytes");

        for (int i = 0; i < numEntries; i++) {
            byte[] data = data2[i].getData();

            byte[][] splitData = new byte[compCount][dataSize];
            int k = 0;

            StringBuilder sb = new StringBuilder();

            for (int x = 0; x < compCount; x++) {
                for (int y = 0; y < dataSize; y++) {
                    splitData[x][y] = data[k];
                    k++;
                }
                reverseArray(splitData[x]);

                for (int y = 0; y < splitData[x].length; y++) {
                    sb.append(String.format("%02X ", splitData[x][y]));
                }
            }
            //System.out.println(sb.toString());

            for (int j = 0; j < compCount; j++) {
                switch(componentType) {
                    case FLOAT32:
                    frameData[i][j] = ByteBuffer.wrap(splitData[j]).getFloat();
                        break;
                    case FLOAT16:
                        byte[] float16data = new byte[4];
                        float16data[0] = 0x00;
                        float16data[1] = 0x00;
                        float16data[2] = splitData[j][0];
                        float16data[3] = splitData[j][1];
    
                        frameData[i][j] = convert16to32(ByteBuffer.wrap(float16data).getInt());
                        break;
                    case INT16:
                    case INT8:
                        frameData[i][j] = (float)ByteBuffer.wrap(splitData[j]).getInt();
                        break;
                    case UINT16:
                        byte[] uint16data = new byte[4];
                        uint16data[0] = 0x00;
                        uint16data[1] = 0x00;
                        uint16data[2] = splitData[j][0];
                        uint16data[3] = splitData[j][1];
                        
                        frameData[i][j] = (float)ByteBuffer.wrap(uint16data).getInt();
                        data = uint16data;
                        break;
                    case UINT8:
                        byte[] uint8data = new byte[4];
                        uint8data[0] = 0x00;
                        uint8data[1] = 0x00;
                        uint8data[2] = 0x00;
                        uint8data[3] = splitData[j][0];
                        
                        frameData[i][j] = (float)ByteBuffer.wrap(uint8data).getInt();
                        data = uint8data;
                        break;
                    default:
                        frameData[i][j] = 0;
                        break;
                }
                //System.out.print(frameData[i][j] + ", ");

                //System.out.println(String.format("0x%08X", frameData[i][j]));
            }
            //System.out.println();
        }

        return frameData;
    }

    private float convert16to32 (int float16bits) {
        
        // first bit is sign
        int sign = float16bits >> 15;

        // next 5 are exponent
        int exp = float16bits >> 10;
        exp = exp & 0b11111;

        // remaining 10 are mantissa
        int mantissa = float16bits & 0b1111111111;

        float output = (sign << 31) | (exp << 23) | mantissa;

        return output;
    }

    private void reverseArray(byte arr[]) {
        byte temp;

        int len2 = arr.length >> 1;

        for (int i = 0; i < len2; i++) {
            temp = arr[i];
            arr[i] = arr[arr.length - i - 1];
            arr[arr.length - i - 1] = temp;
        }
    }
    
    public InterpolationMode getInterpolationMode() {
        return interpolationMode;
    }

    public TimeScale getTimeScale() {
        return timeScale;
    }
    
    public byte getUnk4() {
        return unk4;
    }
    
    class VCTMEntry {
        private final byte[] data;
        
        public VCTMEntry(byte[] data) {
            this.data = data;
        }
        
        public byte[] getData() {
            return data;
        }
    }
    
    @Override
    public int getSize() {
        return 0x20 + Utils.align(data1.length * entrySize, 0x04) + Utils.align(data2.length * coordSize, 0x04);
    }
    
    @Override
    public Payload getType() {
        return Payload.VCTM;
    }
    
    @Override
    public void writeKCAP(Access dest, ResData dataStream) {
        dest.writeInteger(getType().getMagicValue());
        dest.writeInteger(numEntries);
        dest.writeInteger(coordStart);
        dest.writeInteger(entriesStart);
        
        dest.writeByte((byte) interpolationMode.ordinal());
        dest.writeByte((byte) (componentCount << 4 | componentType.ordinal()));
        dest.writeByte((byte) (timeScale.ordinal() << 4 | timeType.ordinal()));
        dest.writeByte(unk4);
        
        dest.writeShort(coordSize);
        dest.writeShort(entrySize);
        dest.writeFloat(unknown4);
        dest.writeFloat(unknown5);
        
        for (VCTMEntry entry : data1)
            for (byte b : entry.getData())
                dest.writeByte(b);
            
        dest.setPosition(Utils.align(dest.getPosition(), 0x4));
        
        for (VCTMEntry entry : data2)
            for (byte b : entry.getData())
                dest.writeByte(b);
            
        long diff = Utils.align(dest.getPosition(), 0x04) - dest.getPosition();
        dest.writeByteArray(new byte[(int) diff]);
    }
    
    public enum InterpolationMode {
        UNK0,
        UNK1,
        UNK2,
        UNK3,
        LINEAR_1D,
        LINEAR_2D,
        LINEAR_3D,
        LINEAR_4D,
        SPHERICAL_LINEAR, // slerp
        NONE,
        UNKA,
        UNKB;
    }
    
    public enum TimeScale {
        EVERY_1_FRAMES(1f),
        EVERY_5_FRAMES(5f),
        EVERY_6_FRAMES(6f),
        EVERY_10_FRAMES(10f),
        EVERY_12_FRAMES(12f),
        EVERY_15_FRAMES(15f),
        EVERY_20_FRAMES(20f),
        EVERY_30_FRAMES(30f),
        
        FPS_1(1 / 1f),
        FPS_5(1 / 5f),
        FPS_6(1 / 6f),
        FPS_10(1 / 10f),
        FPS_12(1 / 12f),
        FPS_15(1 / 15f),
        FPS_20(1 / 20f),
        FPS_30(1 / 30f);
        
        final float value;
        
        private TimeScale(float value) {
            this.value = value;
        }
    }
    
    enum TimeType {
        FLOAT,
        NONE,
        INT16,
        INT8,
        UINT16,
        UINT8;
    }
    
    enum ComponentType {
        FLOAT32,
        FLOAT16,
        INT16,
        INT8,
        UINT16,
        UINT8;
    }
}
