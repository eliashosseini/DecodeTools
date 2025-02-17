package net.digimonworld.decodetools.res.payload;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import org.lwjgl.assimp.AIQuatKey;
import org.lwjgl.assimp.AIVectorKey;

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

    // Position/Scale (3 Values)
    public VCTMPayload(AbstractKCAP parent, List<AIVectorKey> keys, float ticks, float scale) {
        super(parent);
      
        numEntries = keys.size();

        InitializeVCTM(3);

        data1 = new VCTMEntry[numEntries];
        data2 = new VCTMEntry[numEntries];
        
        for (int i = 0; i < numEntries; i++) {

        	int newTime = (int)(Math.round((keys.get(i).mTime() * 333.0f / ticks)));

        	byte[] timeBytes = { (byte) (newTime), (byte) (newTime >> 8) };
        	data1[i] = new VCTMEntry(timeBytes);


            data1[i] = new VCTMEntry(timeBytes);
        }
        
        for (int i = 0; i < numEntries; i++) {
            // Convert key data to float16 bytes
            //short xVal =  Float.floatToFloat16(keys.get(i).mValue().x());
            //short yVal =  Float.floatToFloat16(keys.get(i).mValue().y());
            //short zVal =  Float.floatToFloat16(keys.get(i).mValue().z());

            //byte[] xBytes = { (byte) (xVal), (byte) (xVal >> 8) };
            //byte[] yBytes = { (byte) (yVal), (byte) (yVal >> 8) };
            //byte[] zBytes = { (byte) (zVal), (byte) (zVal >> 8) };

            //byte[] allBytes = {xBytes[0], xBytes[1], yBytes[0], yBytes[1], zBytes[0], zBytes[1] };

        	float x= (keys.get(i).mValue().x());
         	float y= (keys.get(i).mValue().y());
         	float z= (keys.get(i).mValue().z());
         
         	byte[] allBytes = ByteBuffer.allocate(3* Float.BYTES)
                    .order(ByteOrder.LITTLE_ENDIAN) 
                    .putFloat(x)
                    .putFloat(y)
                    .putFloat(z)
                    .array();
            data2[i] = new VCTMEntry(allBytes);
        }
            
    }

    // Rotation
    public VCTMPayload(AbstractKCAP parent, List<AIQuatKey> keys, float ticks) {
        super(parent);
        
        numEntries = keys.size();

        InitializeVCTM(4);

        data1 = new VCTMEntry[numEntries];
        data2 = new VCTMEntry[numEntries];
        
        for (int i = 0; i < numEntries; i++) {

        	int newTime = (int)(Math.round((keys.get(i).mTime() * 333.0f / ticks)));

        	byte[] timeBytes = { (byte) (newTime), (byte) (newTime >> 8) };
        	data1[i] = new VCTMEntry(timeBytes);


            data1[i] = new VCTMEntry(timeBytes);
        } 
        
        for (int i = 0; i < numEntries; i++) {
            // Convert key data to float16 bytes

        	float x= (keys.get(i).mValue().x());
         	float y= (keys.get(i).mValue().y());
         	float z= (keys.get(i).mValue().z());
        	float w= (keys.get(i).mValue().w());
        	
         	//short xVal =  Float.floatToFloat16(keys.get(i).mValue().x());
         	//short yVal =  Float.floatToFloat16(keys.get(i).mValue().y());
         	//short zVal =  Float.floatToFloat16(keys.get(i).mValue().z());
            //short wVal =  Float.floatToFloat16(keys.get(i).mValue().w());

            //byte[] xBytes = { (byte) (xVal), (byte) (xVal >> 8) };
            //byte[] yBytes = { (byte) (yVal), (byte) (yVal >> 8) };
            //byte[] zBytes = { (byte) (zVal), (byte) (zVal >> 8) };
            //byte[] wBytes = { (byte) (wVal), (byte) (wVal >> 8) };

            //byte[] allBytes = {xBytes[0], xBytes[1], yBytes[0], yBytes[1], zBytes[0], zBytes[1], wBytes[0], wBytes[1] };
       
         	byte[] allBytes = ByteBuffer.allocate(4* Float.BYTES)
                    .order(ByteOrder.LITTLE_ENDIAN) 
                    .putFloat(x)
                    .putFloat(y)
                    .putFloat(z)
                    .putFloat(w)
                    .array();
            data2[i] = new VCTMEntry(allBytes);
        }
            
    }

    private void InitializeVCTM(int components) {
        coordSize = (short)(4*components); // byte length of float16 values
        entrySize = 2; // byte length of uint16 values
        entriesStart = 0x20;
        //coordStart = entriesStart + numEntries * entrySize;
        coordStart = Utils.align(entriesStart + numEntries * entrySize, 0x4);
        
        if (components == 3) {
            interpolationMode = InterpolationMode.LINEAR_3D;
        }
        else if (components == 4) {
            interpolationMode = InterpolationMode.LINEAR_4D;
            //interpolationMode = InterpolationMode.SPHERICAL_LINEAR;
        }
        else {
            interpolationMode = InterpolationMode.LINEAR_1D;
        }
        
        componentCount = (byte)(components & 0xff);
        componentType = ComponentType.FLOAT32;
        
        timeScale = TimeScale.EVERY_1_FRAMES;
        timeType = TimeType.UINT16;
        
        unk4 = 0;
        unknown4 = 0;
        unknown5 = 0;
    }

    private float[] cachedFrameList = null;  

    public float[] getFrameTimes() {
        if (cachedFrameList != null) {
            return cachedFrameList;
        }

         cachedFrameList = new float[numEntries];

        for (int i = 0; i < numEntries; i++) {
            byte[] data = data1[i].getData();
            cachedFrameList[i] = convertBytesToTime(data) * timeScale.value;
        }

        return cachedFrameList;
    }

    public float convertBytesToTime(byte[] data) {
        
        float finalVal;
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN); 
        
        switch(timeType) {
            case FLOAT: 
                finalVal = buffer.getFloat(); 
                break;
            case INT16:
                short int16Value = buffer.getShort();
                finalVal = (float) int16Value;
                break;
            case INT8:
                byte int8Value = buffer.get();
                finalVal = (float) int8Value;
                break;
            case UINT16:
                int uint16Value = Short.toUnsignedInt(buffer.getShort()); 
                finalVal = (float) uint16Value;
                break;
            case UINT8:
                int uint8Value = Byte.toUnsignedInt(buffer.get());
                finalVal = (float) uint8Value;
                break;
            default:
                finalVal = 0;
                break;
        }

        return finalVal;
    }

    public float convertBytesToValue(byte[] data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Data array cannot be null or empty.");
        }

        float finalVal = 0;
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN); 

        switch (componentType) {
            case FLOAT32:
                if (data.length < 4) throw new IllegalArgumentException("Insufficient bytes for FLOAT32.");
                finalVal = buffer.getFloat();
                break;
                
            case FLOAT16:
                if (data.length < 2) throw new IllegalArgumentException("Insufficient bytes for FLOAT16.");
                finalVal = Float.float16ToFloat(buffer.getShort()); 
                break;
                
            case INT16:
                if (data.length < 2) throw new IllegalArgumentException("Insufficient bytes for INT16.");
                finalVal = (float) buffer.getShort(); 
                break;
                
            case INT8:
                if (data.length < 1) throw new IllegalArgumentException("Insufficient bytes for INT8.");
                finalVal = (float) buffer.get(); 
                break;
                
            case UINT16:
                if (data.length < 2) throw new IllegalArgumentException("Insufficient bytes for UINT16.");
                finalVal = (float) Short.toUnsignedInt(buffer.getShort());
                break;
                
            case UINT8:
                if (data.length < 1) throw new IllegalArgumentException("Insufficient bytes for UINT8.");
                finalVal = (float) Byte.toUnsignedInt(buffer.get()); // Correct unsigned conversion
                break;
                
            default:
                finalVal = 0;
                break;
        }

        return finalVal;
    }

    public Byte[][] getRawFrameData(int entryIndex) {
        int compCount = getComponentCount();

        int dataSize = coordSize / compCount;

        byte[] data = data2[entryIndex].getData();

        Byte[][] splitData = new Byte[compCount][dataSize];
        int k = 0;

        for (int x = 0; x < compCount; x++) {
            for (int y = 0; y < dataSize; y++) {
                splitData[x][y] = data[k];
                k++;
            }
        }

        return splitData;
    }

    public int getComponentCount() {
        byte[] compCountData = {0x00, 0x00, 0x00, componentCount};
        int compCount = ByteBuffer.wrap(compCountData).getInt();
        return compCount;
    }

    public int getNumEntries() {
        return numEntries;
    }

    public int GetValueBytes() {
        if (componentType == ComponentType.FLOAT32) {
            return 4;
        }
        else if (componentType == ComponentType.INT8 || componentType == ComponentType.UINT8) {
            return 1;
        }
        else {
            return 2;
        }
    }
    
    @SuppressWarnings("exports")
	public ComponentType getComponentType() {
        return componentType;
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

        public float getValue() {
            return value;
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
    
    public enum ComponentType {
        FLOAT32,
        FLOAT16,
        INT16,
        INT8,
        UINT16,
        UINT8;
    }
}