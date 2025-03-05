package net.digimonworld.decodetools.gui;

import javax.swing.*;
import javax.swing.GroupLayout.Alignment;
import javax.swing.table.DefaultTableModel;
import java.util.List;
import net.digimonworld.decodetools.Main;
import net.digimonworld.decodetools.res.kcap.AbstractKCAP;
import net.digimonworld.decodetools.res.kcap.TDTMKCAP;
import net.digimonworld.decodetools.res.kcap.TDTMKCAP.TDTMEntry;

public class TDTMPanel extends PayloadPanel {
    private static final long serialVersionUID = -6616813521331311399L;

    private TDTMKCAP tdtm;

    private final JScrollPane scrollPane = new JScrollPane();
    private final JPanel panel = new JPanel();
    
    private final JLabel time1Label = new JLabel("Start Time:");
    private final JLabel time2Label = new JLabel("End Time:");
    private final JLabel time3Label = new JLabel("Loop Start:");
    private final JLabel time4Label = new JLabel("Loop End:");
    private final JLabel qstmCountLabel = new JLabel("QSTM Count:");
    private final JLabel vctmCountLabel = new JLabel("VCTM Count:");
    
    private final JSpinner time1spinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, Double.MAX_VALUE, 1.0));
    private final JSpinner time2spinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, Double.MAX_VALUE, 1.0));
    private final JSpinner time3spinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, Double.MAX_VALUE, 1.0));
    private final JSpinner time4spinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, Double.MAX_VALUE, 1.0));

    private final JTable entryTable;
    private final DefaultTableModel tableModel;
    
    public TDTMPanel(Object selected) {
        setSelectedFile(selected);

        // Setup table for displaying entries
        String[] columnNames = {"Mode", "Transform Type", "Joint ID", "QSTM ID"};
        tableModel = new DefaultTableModel(columnNames, 0);
        entryTable = new JTable(tableModel);
        JScrollPane tableScrollPane = new JScrollPane(entryTable);
        
        // Set up layout
        GroupLayout groupLayout = new GroupLayout(this);
        this.setLayout(groupLayout);

        groupLayout.setHorizontalGroup(
        	    groupLayout.createSequentialGroup()
        	        .addContainerGap()
        	        .addGroup(groupLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
        	            .addGroup(groupLayout.createSequentialGroup()
        	                .addGroup(groupLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
        	                    .addComponent(time1Label)
        	                    .addComponent(time1spinner, GroupLayout.PREFERRED_SIZE, 100, GroupLayout.PREFERRED_SIZE))
        	                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
        	                .addGroup(groupLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
        	                    .addComponent(time2Label)
        	                    .addComponent(time2spinner, GroupLayout.PREFERRED_SIZE, 100, GroupLayout.PREFERRED_SIZE))
        	                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
        	                .addGroup(groupLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
        	                    .addComponent(time3Label)
        	                    .addComponent(time3spinner, GroupLayout.PREFERRED_SIZE, 100, GroupLayout.PREFERRED_SIZE))
        	                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
        	                .addGroup(groupLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
        	                    .addComponent(time4Label)
        	                    .addComponent(time4spinner, GroupLayout.PREFERRED_SIZE, 100, GroupLayout.PREFERRED_SIZE)))
        	            .addGroup(groupLayout.createSequentialGroup()
        	                .addComponent(qstmCountLabel)
        	                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
        	                .addComponent(vctmCountLabel))
        	            .addComponent(tableScrollPane, GroupLayout.PREFERRED_SIZE, 400, GroupLayout.PREFERRED_SIZE))
        	        .addContainerGap(20, Short.MAX_VALUE)
        	);

        	groupLayout.setVerticalGroup(
        	    groupLayout.createSequentialGroup()
        	        .addContainerGap()
        	        .addGroup(groupLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
        	            .addComponent(time1Label)
        	            .addComponent(time2Label)
        	            .addComponent(time3Label)
        	            .addComponent(time4Label))
        	        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
        	        .addGroup(groupLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
        	            .addComponent(time1spinner, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
        	            .addComponent(time2spinner, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
        	            .addComponent(time3spinner, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
        	            .addComponent(time4spinner, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
        	        .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
        	        .addGroup(groupLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
        	            .addComponent(qstmCountLabel)
        	            .addComponent(vctmCountLabel))
        	        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
        	        .addComponent(tableScrollPane, GroupLayout.PREFERRED_SIZE, 200, GroupLayout.PREFERRED_SIZE)
        	        .addContainerGap()
        	);

        	// Set the layout for the scrollPane's viewport
        	scrollPane.setViewportView(panel);
}
  
    @Override
    public void setSelectedFile(Object file) {
        if (file == null)
            return;

        if (!(file instanceof AbstractKCAP)) {
            Main.LOGGER.warning("Tried to select non-KCAP File in TDTMPanel.");
            return;
        }

        if (((AbstractKCAP) file).getKCAPType() != AbstractKCAP.KCAPType.TDTM) {
            Main.LOGGER.warning("Tried to select non-TDTM KCAP File in TDTMPanel.");
            return;
        }

        this.tdtm = (TDTMKCAP) file;

        time1spinner.setValue(tdtm.getTime1()); 
        time2spinner.setValue(tdtm.getTime2());
        time3spinner.setValue(tdtm.getTime3());
        time4spinner.setValue(tdtm.getTime4());

        // Re-add listeners
        time1spinner.addChangeListener(e -> {
            if (tdtm != null) {
                tdtm.setTime1((float) ((Number) time1spinner.getValue()).doubleValue());
            }
        });

        time2spinner.addChangeListener(e -> {
            if (tdtm != null) {
                tdtm.setTime2((float) ((Number) time2spinner.getValue()).doubleValue());
            }
        });

        time3spinner.addChangeListener(e -> {
            if (tdtm != null) {
                tdtm.setTime3((float) ((Number) time3spinner.getValue()).doubleValue());
            }
        });

        time4spinner.addChangeListener(e -> {
            if (tdtm != null) {
                tdtm.setTime4((float) ((Number) time4spinner.getValue()).doubleValue());
            }
        });

        // Update QSTM and VCTM count labels
        qstmCountLabel.setText("QSTM Count: " + tdtm.getQstmCount());
        vctmCountLabel.setText("VCTM Count: " + tdtm.getVctmCount());

        // Populate the entry table
        updateTable();
    }


    private void updateTable() {
        tableModel.setRowCount(0); // Clear the table
        List<TDTMEntry> entries = tdtm.getTdtmEntries();

        for (TDTMEntry entry : entries) {
            tableModel.addRow(new Object[]{
                entry.getMode(),            // Mode
                String.format("0x%02X", entry.getTransformType()), // Transform Type as hex
                entry.getjointId(),         // Joint ID
                entry.getqstmId()           // QSTM ID
            });
        }
    }
}
