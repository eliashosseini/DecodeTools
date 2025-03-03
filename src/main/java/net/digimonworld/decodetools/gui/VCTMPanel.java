package net.digimonworld.decodetools.gui;

import javax.swing.*;
import javax.swing.GroupLayout.Alignment;
import javax.swing.table.DefaultTableModel;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import net.digimonworld.decodetools.Main;
import net.digimonworld.decodetools.res.payload.VCTMPayload;

public class VCTMPanel extends PayloadPanel {
    private static final long serialVersionUID = 1L;

    private VCTMPayload vctm;

    private final JTable entryTable;
    private final DefaultTableModel tableModel;

    public VCTMPanel(Object selected) {
        setSelectedFile(selected);

        String[] columnNames = {
        	    "Frame Index", "Time", "Time Scale","Component Count", 
        	    "Component Type", "Interpolation", 
        	    "X", "Y", "Z", "W"
        	};
         tableModel = new DefaultTableModel(columnNames, 0);        
         entryTable = new JTable(tableModel);
        JScrollPane tableScrollPane = new JScrollPane(entryTable);

        // Set up layout
        GroupLayout groupLayout = new GroupLayout(this);
        this.setLayout(groupLayout);

        groupLayout.setHorizontalGroup(
            groupLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(tableScrollPane, GroupLayout.PREFERRED_SIZE, 800, GroupLayout.PREFERRED_SIZE)
                .addContainerGap(20, Short.MAX_VALUE)
        );

        groupLayout.setVerticalGroup(
            groupLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(tableScrollPane, GroupLayout.PREFERRED_SIZE, 600, GroupLayout.PREFERRED_SIZE)
                .addContainerGap()
        );
    }

    @Override
    public void setSelectedFile(Object file) {
        if (file == null)
            return;

        if (!(file instanceof VCTMPayload)) {
            Main.LOGGER.warning("Tried to select non-VCTM File in VCTMPanel.");
            return;
        }

        this.vctm = (VCTMPayload) file;
        updateTable();
    }


    private void updateTable() {
        tableModel.setRowCount(0); // Clear table

        // Retrieve frame times
        float[] frameTimes = vctm.getFrameTimes();

        int numEntries = frameTimes.length;
        int componentCount = vctm.getComponentCount();
        String componentType = vctm.getComponentType().toString();
        String interpolation = vctm.getInterpolationMode().toString();

          for (int i = 0; i < numEntries; i++) {
            Byte[][] rawFrameData = vctm.getRawFrameData(i);

            // Extract raw values based on component type
            String x = "", y = "", z = "", w = "";
            for (int j = 0; j < componentCount; j++) {
                byte[] componentBytes = new byte[rawFrameData[j].length];
                for (int k = 0; k < rawFrameData[j].length; k++) {
                    componentBytes[k] = rawFrameData[j][k];
                }

                String rawValue;
                switch (componentType) {
                    case "FLOAT16":
                        short float16Bits = ByteBuffer.wrap(componentBytes).order(ByteOrder.LITTLE_ENDIAN).getShort();
                        rawValue = String.format("%.10f", Float.float16ToFloat(float16Bits));
                        break;
                    case "BYTE":
                        rawValue = String.valueOf(componentBytes[0]);
                        break;
                    case "SHORT":
                        short shortValue = ByteBuffer.wrap(componentBytes).order(ByteOrder.LITTLE_ENDIAN).getShort();
                        rawValue = String.valueOf(shortValue);
                        break;
                    case "FLOAT32":
                        float floatValue = ByteBuffer.wrap(componentBytes).order(ByteOrder.LITTLE_ENDIAN).getFloat();
                     //   rawValue = String.valueOf(floatValue);
                        rawValue = String.format("%.10f", floatValue);  // Shows up to 6 decimal places, no scientific notation

                        break;
                    default:
                        rawValue = "UNKNOWN";
                        break;
                }

                // Assign values to respective components
                if (j == 0) x = rawValue;
                else if (j == 1) y = rawValue;
                else if (j == 2) z = rawValue;
                else if (j == 3) w = rawValue;
            }

            // Add row to the table
            tableModel.addRow(new Object[]{
                i,                                    // Frame Index
                frameTimes[i], 						  // Time
                vctm.getTimeScale(),				  //Time Scale
                componentCount,                       // Component Count
                componentType,                        // Component Type
                interpolation,                        // Interpolation
                x,                                    // X
                y,                                    // Y
                z,                                    // Z
                w                                     // W
            });
        }
    }



}
