package net.digimonworld.decodetools.gui;

import javax.swing.*;
import javax.swing.GroupLayout.Alignment;
import javax.swing.table.DefaultTableModel;
import java.util.List;
import net.digimonworld.decodetools.Main;
import net.digimonworld.decodetools.res.kcap.AbstractKCAP;
import net.digimonworld.decodetools.res.payload.QSTMPayload;
import net.digimonworld.decodetools.res.payload.qstm.*;

public class QSTMPanel extends PayloadPanel {
    private static final long serialVersionUID = 1L;

    private QSTMPayload qstm;
    
    private final JTable entryTable;
    private final DefaultTableModel tableModel;
    
    public QSTMPanel(Object selected) {
        setSelectedFile(selected);

        // Setup table for displaying entries
        String[] columnNames = {"Type", "Axis", "Mode", "Values", "Dest", "Src", "VCTM ID"};
        tableModel = new DefaultTableModel(columnNames, 0);
        entryTable = new JTable(tableModel);
        JScrollPane tableScrollPane = new JScrollPane(entryTable);
        
        // Set up layout
        GroupLayout groupLayout = new GroupLayout(this);
        this.setLayout(groupLayout);

        groupLayout.setHorizontalGroup(
            groupLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(tableScrollPane, GroupLayout.PREFERRED_SIZE, 600, GroupLayout.PREFERRED_SIZE)
                .addContainerGap(20, Short.MAX_VALUE)
        );

        groupLayout.setVerticalGroup(
            groupLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(tableScrollPane, GroupLayout.PREFERRED_SIZE, 400, GroupLayout.PREFERRED_SIZE)
                .addContainerGap()
        );
    }

    @Override
    public void setSelectedFile(Object file) {
        if (file == null)
            return;

        if (!(file instanceof QSTMPayload)) {
            Main.LOGGER.warning("Tried to select non-QSTM File in QSTMPanel.");
            return;
        }

        this.qstm = (QSTMPayload) file;
        updateTable();
    }

    private void updateTable() {
        tableModel.setRowCount(0); // Clear table
        List<QSTMEntry> entries = qstm.getEntries();

        for (QSTMEntry entry : entries) {
            if (entry instanceof QSTM00Entry) {
                QSTM00Entry e = (QSTM00Entry) entry;
                tableModel.addRow(new Object[]{
                    e.getType(),
                    e.getAxis(),
                    e.getMode(),
                    e.getValues(),
                    "-", "-", "-"
                });
            } 
            else if (entry instanceof QSTM01Entry) {
                QSTM01Entry e = (QSTM01Entry) entry;
                tableModel.addRow(new Object[]{
                    e.getType(),
                    "-",
                    e.getMode(),
                    "-",
                    e.getDestId(),
                    e.getSrcId(),
                    "-"
                });
            } 
            else if (entry instanceof QSTM02Entry) {
                QSTM02Entry e = (QSTM02Entry) entry;
                tableModel.addRow(new Object[]{
                    e.getType(),
                    e.getAxis(),
                    "-",
                    "-",
                    "-",
                    "-",
                    e.getVctmId()
                });
            }
        }
    }
}
