package net.digimonworld.decodetools.res.payload;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import net.digimonworld.decodetools.Main;
import net.digimonworld.decodetools.core.Access;
import net.digimonworld.decodetools.res.ResData;
import net.digimonworld.decodetools.res.ResPayload;
import net.digimonworld.decodetools.res.kcap.AbstractKCAP;
import net.digimonworld.decodetools.res.payload.hsem.HSEMEntry;

/*-
 * HSEM "head" (0x40 byte)
 *  ID (4 byte)
 *  size (4 byte)
 *  unk1 (4 byte)
 *  unk2 (4 byte)
 *  10x unk3 (float)
 *  unk3 (4 byte)
 *  unk4 (4 byte)
 * HSEM "payload" (size - 0x40 byte)
 *  variable amount of payload entries
 *   id (short)
 *   size (short)
 *   data (size - 4)
 * 
 * 
 * 0x58                     HSEM into data
 * 0x14 byte                intro data
 * 0x04 * short(0x12) byte  joint data
 * 0x14 byte                outro data
 */
public class HSEMPayload extends ResPayload {
    private int id;
    // int size
    // int numEntries;
    private short unknown2_1;
    private short unknown2_2;
    
    /*
     * visible distance X?
     * visible rotation? 0?
     * visible distance Y?
     * rotation something?
     */
    private float[] headerData = new float[10];
    private int unknown3;
    private int unknown4;
    
    private List<HSEMEntry> entries = new ArrayList<>();
    
    public HSEMPayload(AbstractKCAP parent, List<HSEMEntry> entries, int id, short unknown2_1, short unknown2_2, float[] headerData, int unknown3, int unknown4) {
        super(parent);
        
        this.id = id;
        this.unknown2_1 = unknown2_1;
        this.unknown2_2 = unknown2_2;
        this.headerData = Arrays.copyOf(headerData, 10);
        this.unknown3 = unknown3;
        this.unknown4 = unknown4;
        this.entries.addAll(entries);
    }
    
    public HSEMPayload(Access source, int dataStart, AbstractKCAP parent, int size, String name) {
        super(parent);
        
        long start = source.getPosition();
        
        this.id = source.readInteger();
        source.readInteger();
        int numEntries = source.readInteger();
        this.unknown2_1 = source.readShort();
        this.unknown2_2 = source.readShort();
        
        for (int i = 0; i < 10; i++) {
            this.headerData[i] = source.readFloat();
        }
        
        this.unknown3 = source.readInteger();
        this.unknown4 = source.readInteger();
        
        for(int i = 0; i < numEntries; i++)
            entries.add(HSEMEntry.loadEntry(source));
        
        if(source.getPosition() - start != size)
            Main.LOGGER.warning("HSEM Payload was smaller than advertised.");
    }
    
    @Override
    public int getSize() {
        return 0x40 + entries.stream().collect(Collectors.summingInt(HSEMEntry::getSize));
    }
    
    @Override
    public Payload getType() {
        return Payload.HSEM;
    }
    
    @Override
    public void writeKCAP(Access dest, ResData dataStream) {
        dest.writeInteger(id);
        dest.writeInteger(getSize());
        
        dest.writeInteger(entries.size());
        dest.writeShort(unknown2_1);
        dest.writeShort(unknown2_2);
        
        for (float f : headerData)
            dest.writeFloat(f);
        
        dest.writeInteger(unknown3);
        dest.writeInteger(unknown4);
        
        entries.forEach(a -> a.writeKCAP(dest));
    }
    
    public List<HSEMEntry> getEntries() {
        return entries;
    }
    
    public float[] getHeaderData() {
        return headerData;
    }
    
    public int getId() {
        return id;
    }
    
    public int getUnknown2_1() {
        return unknown2_1;
    }
    
    public short getUnknown2_2() {
        return unknown2_2;
    }
    
    public int getUnknown3() {
        return unknown3;
    }

    public int getUnknown4() {
        return unknown4;
    }
}
