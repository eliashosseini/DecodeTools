package net.digimonworld.decodetools.gui;

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.FloatBuffer;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.swing.AbstractAction;
import javax.swing.AbstractListModel;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.ListSelectionModel;

import org.joml.AxisAngle4f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.assimp.AIAnimation;
import org.lwjgl.assimp.AIBone;
import org.lwjgl.assimp.AIColor4D;
import org.lwjgl.assimp.AIFace;
import org.lwjgl.assimp.AIMaterial;
import org.lwjgl.assimp.AIMatrix4x4;
import org.lwjgl.assimp.AIMesh;

import org.lwjgl.assimp.AINode;
import org.lwjgl.assimp.AIPropertyStore;
import org.lwjgl.assimp.AIQuaternion;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.AIString;
import org.lwjgl.assimp.AIVector3D;
import org.lwjgl.assimp.AIVertexWeight;
import org.lwjgl.assimp.Assimp;

import de.javagl.jgltf.impl.v2.Material;
import de.javagl.jgltf.model.GltfModel;

import de.javagl.jgltf.model.io.GltfModelReader;
import net.digimonworld.decodetools.Main;
import net.digimonworld.decodetools.core.Utils;
//import net.digimonworld.decodetools.gui.HSEMData.HSEM07Data;
//import net.digimonworld.decodetools.gui.HSEMData.MeshInfo;
//import net.digimonworld.decodetools.gui.HSEMData.UnkData;
import net.digimonworld.decodetools.gui.util.FunctionAction;
import net.digimonworld.decodetools.gui.util.LinAlg.Vector3;
import net.digimonworld.decodetools.res.ResPayload;
import net.digimonworld.decodetools.res.ResPayload.Payload;
import net.digimonworld.decodetools.res.kcap.HSEMKCAP;
import net.digimonworld.decodetools.res.kcap.HSMPKCAP;
import net.digimonworld.decodetools.res.kcap.TNOJKCAP;
import net.digimonworld.decodetools.res.kcap.XDIPKCAP;
import net.digimonworld.decodetools.res.kcap.XTVPKCAP;
import net.digimonworld.decodetools.res.kcap.TDTMKCAP;
import net.digimonworld.decodetools.res.kcap.AbstractKCAP.KCAPType;
import net.digimonworld.decodetools.res.kcap.AbstractKCAP;
import net.digimonworld.decodetools.res.kcap.NormalKCAP;
import net.digimonworld.decodetools.res.payload.HSEMPayload;
import net.digimonworld.decodetools.res.payload.TNOJPayload;
import net.digimonworld.decodetools.res.payload.XDIOPayload;
import net.digimonworld.decodetools.res.payload.XTVOPayload;
import net.digimonworld.decodetools.res.payload.hsem.HSEM07Entry;
import net.digimonworld.decodetools.res.payload.hsem.HSEMDrawEntry;
import net.digimonworld.decodetools.res.payload.hsem.HSEMEntry;
import net.digimonworld.decodetools.res.payload.hsem.HSEMJointEntry;
import net.digimonworld.decodetools.res.payload.hsem.HSEMMaterialEntry;
import net.digimonworld.decodetools.res.payload.hsem.HSEMTextureEntry;
import net.digimonworld.decodetools.res.payload.xdio.XDIOFace;
import net.digimonworld.decodetools.res.payload.xtvo.XTVOAttribute;
import net.digimonworld.decodetools.res.payload.xtvo.XTVORegisterType;
import net.digimonworld.decodetools.res.payload.xtvo.XTVOValueType;
import net.digimonworld.decodetools.res.payload.xtvo.XTVOVertex;
import net.digimonworld.decodetools.res.payload.hsem.HSEM07Entry;

import java.io.FileInputStream;
import java.io.InputStream;

import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.swing.JComboBox;
import javax.swing.DefaultComboBoxModel;

public class ModelImporter extends PayloadPanel {
    private static final float[] IDENTITY_MATRIX = { 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f,
                                                     0.0f, 0.0f, 0.0f, 0.0f, 1.0f };
    private static final String[] JOINT_PREFIXES = { "J_", "AT_", };

    private static final int IMPORT_FLAGS = Assimp.aiProcess_Triangulate | Assimp.aiProcess_LimitBoneWeights
                                            | Assimp.aiProcess_SplitByBoneCount | Assimp.aiProcess_JoinIdenticalVertices
                                            | Assimp.aiProcess_OptimizeMeshes;

    private static final AIPropertyStore importProperties = Assimp.aiCreatePropertyStore();
    static {
        Assimp.aiSetImportPropertyInteger(importProperties, Assimp.AI_CONFIG_PP_SBBC_MAX_BONES, 16);
        Assimp.aiSetImportPropertyInteger(importProperties, Assimp.AI_CONFIG_PP_LBW_MAX_WEIGHTS, 4);
    }

    private HSMPKCAP rootKCAP;
    List<TDTMKCAP> tdtmKCAPs;
    private AIScene scene;
    //private HSEMData hsemData;

    // persistent, loaded or via GUI
    private List<AINode> jointNodes = new ArrayList<>();

    // Swing garbage
    private final JLabel lblInput = new JLabel("Input:");
    private final JLabel lblnone = new JLabel("(None)");
    private final JButton btnSelect = new JButton("Open");
    private final JList<String> list = new JList<>();
    private final JScrollPane scrollPane = new JScrollPane();
    private final JButton btnDown = new JButton("↓");
    private final JButton btnUp = new JButton("↑");
    private final JPanel panel = new JPanel();
    private final JButton btnSave = new JButton("Update HSMP");
    private final JLabel lblScale = new JLabel("Scale:");
    private final JSpinner spinner = new JSpinner();
    private final JPanel panel_1 = new JPanel();
    private final JComboBox<Integer> comboBox = new JComboBox<>();
    private final JLabel lblNewLabel = new JLabel("Shader:");
    private final JButton btnNewButton = new JButton("Joints to OBJ");
    private final JButton btnExportDAE = new JButton("Export to DAE");
    private final JButton btnExportglTF = new JButton("Export to glTF");

    // generated

    public ModelImporter(HSMPKCAP rootKCAP) {

        btnNewButton.addActionListener(a -> {
            if (this.rootKCAP == null)
                return;

            JFileChooser fileDialogue = new JFileChooser("./Output");
            fileDialogue.setFileSelectionMode(JFileChooser.FILES_ONLY);
            fileDialogue.showSaveDialog(null);

            if (fileDialogue.getSelectedFile() == null)
                return;

            try (PrintStream out = new PrintStream(fileDialogue.getSelectedFile())) {
                tnojToObj(out);
            }
            catch (FileNotFoundException e) {
                //
            }
        });

        btnExportglTF.addActionListener(a -> {
            JFileChooser fileDialogue = new JFileChooser("./Output");
            fileDialogue.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fileDialogue.showSaveDialog(null);

            if (fileDialogue.getSelectedFile() == null)
                return;
            saveGLTF(fileDialogue.getSelectedFile());

        });

        btnExportDAE.addActionListener(a -> {
            if (this.rootKCAP == null)
                return;

            JFileChooser fileDialogue = new JFileChooser("./Output");
            fileDialogue.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fileDialogue.showSaveDialog(null);

            if (fileDialogue.getSelectedFile() == null)
                return;

            saveModel(fileDialogue.getSelectedFile());
        });

        lblScale.setLabelFor(spinner);
        lblNewLabel.setLabelFor(comboBox);
        comboBox.setModel(new DefaultComboBoxModel<Integer>(new Integer[] { 0, 1, 2, 3, 4, 5, 6, 7, 8 }));
        comboBox.setSelectedIndex(8);
        spinner.setModel(new SpinnerNumberModel(0f, null, null, 0.001f));
        setSelectedFile(rootKCAP);
        setupLayout();

        btnSave.setAction(new FunctionAction("Update HSMP", a -> loadModel()));

        btnUp.setAction(new AbstractAction("↑") {
            @Override
            public void actionPerformed(ActionEvent e) {
                int selIndex = list.getSelectedIndex();
                if (selIndex == -1)
                    return;

                if (selIndex <= 0)
                    return;

                AINode preNode = jointNodes.get(selIndex - 1);
                AINode node = jointNodes.get(selIndex);

                jointNodes.set(selIndex, preNode);
                jointNodes.set(selIndex - 1, node);
                list.setSelectedIndex(selIndex - 1);
                list.requestFocus();
                list.repaint();
            }
        });

        btnDown.setAction(new AbstractAction("↓") {
            @Override
            public void actionPerformed(ActionEvent e) {
                int selIndex = list.getSelectedIndex();
                if (selIndex == -1)
                    return;

                if (selIndex + 1 >= jointNodes.size())
                    return;

                AINode nextNode = jointNodes.get(selIndex + 1);
                AINode node = jointNodes.get(selIndex);

                jointNodes.set(selIndex, nextNode);
                jointNodes.set(selIndex + 1, node);
                list.setSelectedIndex(selIndex + 1);
                list.requestFocus();
                list.repaint();
            }
        });

        btnSelect.setAction(new AbstractAction("Open") {

            @Override
            public void actionPerformed(ActionEvent e) {
                if (ModelImporter.this.rootKCAP == null)
                    return;

                JFileChooser fileDialogue = new JFileChooser("./Input");
                fileDialogue.setFileSelectionMode(JFileChooser.FILES_ONLY);
                fileDialogue.showOpenDialog(null);

                if (fileDialogue.getSelectedFile() == null)
                    return;

                loadGLTFModel(fileDialogue.getSelectedFile().getPath());
                scene = Assimp.aiImportFileExWithProperties(fileDialogue.getSelectedFile().getPath(), IMPORT_FLAGS,
                                                            null, importProperties);

                if (scene == null) {
                    Main.LOGGER.severe(() -> "assimp Error while importing model: " + Assimp.aiGetErrorString());
                    return;
                }

                jointNodes = extractJointNodes(scene.mRootNode());  // Use a method that respects GLTF order
                float scale = calculateModelScale();
                spinner.setValue(scale);

                lblnone.setText(fileDialogue.getSelectedFile().getPath());

                AbstractListModel<String> model = new AbstractListModel<String>() {
                    @Override
                    public String getElementAt(int index) {
                        return jointNodes.get(index).mName().dataString();
                    }

                    @Override
                    public int getSize() {
                        return jointNodes.size();
                    }
                };
                list.setModel(model);
            }
        });
    }

    private void setupLayout() {
        GroupLayout groupLayout = new GroupLayout(this);
        groupLayout.setHorizontalGroup(groupLayout.createParallelGroup(Alignment.TRAILING)
                                                  .addGroup(groupLayout.createSequentialGroup().addContainerGap()
                                                                       .addGroup(groupLayout.createParallelGroup(Alignment.TRAILING)
                                                                                            .addGroup(groupLayout.createSequentialGroup()
                                                                                                                 .addComponent(lblInput)
                                                                                                                 .addGap(9)
                                                                                                                 .addComponent(lblnone,
                                                                                                                               GroupLayout.DEFAULT_SIZE,
                                                                                                                               343,
                                                                                                                               Short.MAX_VALUE))
                                                                                            .addComponent(panel_1,
                                                                                                          GroupLayout.DEFAULT_SIZE,
                                                                                                          384,
                                                                                                          Short.MAX_VALUE))
                                                                       .addGap(12)
                                                                       .addGroup(groupLayout.createParallelGroup(Alignment.LEADING,
                                                                                                                 false)
                                                                                            .addGroup(groupLayout.createSequentialGroup()
                                                                                                                 .addComponent(btnSelect)
                                                                                                                 .addPreferredGap(ComponentPlacement.RELATED,
                                                                                                                                  GroupLayout.DEFAULT_SIZE,
                                                                                                                                  Short.MAX_VALUE)
                                                                                                                 .addComponent(btnSave,
                                                                                                                               GroupLayout.PREFERRED_SIZE,
                                                                                                                               113,
                                                                                                                               GroupLayout.PREFERRED_SIZE))
                                                                                            .addComponent(scrollPane,
                                                                                                          GroupLayout.PREFERRED_SIZE,
                                                                                                          180,
                                                                                                          GroupLayout.PREFERRED_SIZE))
                                                                       .addContainerGap()));
        groupLayout.setVerticalGroup(groupLayout.createParallelGroup(Alignment.LEADING)
                                                .addGroup(groupLayout.createSequentialGroup().addContainerGap()
                                                                     .addGroup(groupLayout.createParallelGroup(Alignment.BASELINE)
                                                                                          .addComponent(lblnone)
                                                                                          .addComponent(btnSelect)
                                                                                          .addComponent(btnSave)
                                                                                          .addComponent(lblInput))
                                                                     .addPreferredGap(ComponentPlacement.UNRELATED)
                                                                     .addGroup(groupLayout.createParallelGroup(Alignment.LEADING)
                                                                                          .addComponent(panel_1,
                                                                                                        GroupLayout.DEFAULT_SIZE,
                                                                                                        381,
                                                                                                        Short.MAX_VALUE)
                                                                                          .addComponent(scrollPane,
                                                                                                        GroupLayout.DEFAULT_SIZE,
                                                                                                        381,
                                                                                                        Short.MAX_VALUE))
                                                                     .addContainerGap()));
        GroupLayout gl_panel_1 = new GroupLayout(panel_1);
        gl_panel_1.setHorizontalGroup(gl_panel_1.createParallelGroup(Alignment.TRAILING)
                                                .addGroup(gl_panel_1.createSequentialGroup()
                                                                    .addGroup(gl_panel_1.createParallelGroup(Alignment.LEADING)
                                                                                        .addGroup(gl_panel_1.createSequentialGroup()
                                                                                                            .addComponent(lblScale)
                                                                                                            .addPreferredGap(ComponentPlacement.RELATED)
                                                                                                            .addComponent(spinner,
                                                                                                                          GroupLayout.PREFERRED_SIZE,
                                                                                                                          75,
                                                                                                                          GroupLayout.PREFERRED_SIZE)
                                                                                                            .addPreferredGap(ComponentPlacement.RELATED)
                                                                                                            .addComponent(lblNewLabel)
                                                                                                            .addPreferredGap(ComponentPlacement.RELATED)
                                                                                                            .addComponent(comboBox,
                                                                                                                          GroupLayout.PREFERRED_SIZE,
                                                                                                                          53,
                                                                                                                          GroupLayout.PREFERRED_SIZE))
                                                                                        .addGroup(gl_panel_1.createSequentialGroup()
                                                                                                            .addContainerGap()
                                                                                                            .addComponent(btnNewButton)
                                                                                                            .addPreferredGap(ComponentPlacement.RELATED)
                                                                                                            .addComponent(btnExportDAE)
                                                                                                            .addPreferredGap(ComponentPlacement.RELATED)
                                                                                                            .addComponent(btnExportglTF)))

                                                                    .addContainerGap(26, Short.MAX_VALUE)));
        gl_panel_1.setVerticalGroup(gl_panel_1.createParallelGroup(Alignment.LEADING)
                                              .addGroup(gl_panel_1.createSequentialGroup()
                                                                  .addGroup(gl_panel_1.createParallelGroup(Alignment.BASELINE)
                                                                                      .addComponent(lblScale)
                                                                                      .addComponent(spinner,
                                                                                                    GroupLayout.PREFERRED_SIZE,
                                                                                                    GroupLayout.DEFAULT_SIZE,
                                                                                                    GroupLayout.PREFERRED_SIZE)
                                                                                      .addComponent(lblNewLabel)
                                                                                      .addComponent(comboBox,
                                                                                                    GroupLayout.PREFERRED_SIZE,
                                                                                                    GroupLayout.DEFAULT_SIZE,
                                                                                                    GroupLayout.PREFERRED_SIZE))
                                                                  .addPreferredGap(ComponentPlacement.RELATED, 190,
                                                                                   Short.MAX_VALUE)
                                                                  .addGroup(gl_panel_1.createParallelGroup(Alignment.BASELINE)
                                                                                      .addComponent(btnNewButton)
                                                                                      .addComponent(btnExportDAE)
                                                                                      .addComponent(btnExportglTF))
                                                                  .addContainerGap()));
        panel_1.setLayout(gl_panel_1);
        scrollPane.setViewportView(list);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        scrollPane.setColumnHeaderView(panel);
        panel.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
        panel.add(btnUp);
        panel.add(btnDown);
        setLayout(groupLayout);
    }

    public void saveModel(File output) {
        try {
            new ColldadaExporter(rootKCAP).export(output);
        }
        catch (TransformerException | ParserConfigurationException e) {
            // Handle TransformerException or ParserConfigurationException
            e.printStackTrace();
        }
    }

    public void saveGLTF(File output) {
        new GLTFExporter(rootKCAP).export(output);
    }

    public int countUniqueHSEMIds(Map<Integer, Short> hsemPairs) {
        Set<Short> uniqueIds = new HashSet<>();
        for (Short hsemId : hsemPairs.values()) {
            uniqueIds.add(hsemId);
        }
        return uniqueIds.size();
    }

    private void addMaterialEntry(List<HSEMEntry> entries, int materialId) {

        entries.add(new HSEMMaterialEntry((short) 0, (short) materialId));
    }
    
    public GltfModel loadGLTFModel(String filePath) {
        GltfModelReader reader = new GltfModelReader();
        try {
            return reader.read(Paths.get(filePath));
        }
        catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private Map<Short, Short> processBoneMappingAndWeights(AIMesh mesh,
                                                           List<SortedMap<XTVOAttribute, List<Number>>> vertices,
                                                           XTVOAttribute idxAttrib, XTVOAttribute wgtAttrib,
                                                           List<TNOJPayload> tnoj) {
        Map<Short, Short> boneMapping = new HashMap<>();

        if (mesh.mNumBones() != 0) {
            for (short j = 0; j < mesh.mNumBones(); j++) {
                AIBone bone = AIBone.create(mesh.mBones().get(j));
                AIVertexWeight.Buffer weights = bone.mWeights();

                short id = 0;
                for (; id < tnoj.size(); id++)
                    if (tnoj.get(id).getName().equals(bone.mName().dataString()))
                        break;

                boneMapping.put(j, id);

                if (weights != null) {
                    for (int k = 0; k < bone.mNumWeights(); k++) {
                        AIVertexWeight weight = weights.get(k);
                        int vertexId = weight.mVertexId();
                        float val = weight.mWeight() * 255;

                        vertices.get(vertexId).computeIfAbsent(idxAttrib, a -> new ArrayList<Number>()).add(j * 3);
                        vertices.get(vertexId).computeIfAbsent(wgtAttrib, a -> new ArrayList<Number>()).add(val);
                    }
                }
            }
            vertices.forEach(a -> {
                a.putIfAbsent(wgtAttrib, new ArrayList<>());
                a.putIfAbsent(idxAttrib, new ArrayList<>());
                Utils.padList(a.get(wgtAttrib), wgtAttrib.getCount(), 0);
                Utils.padList(a.get(idxAttrib), idxAttrib.getCount(), 0);
            });
        }
        return boneMapping;
    }

    private static String getMaterialAlphaMode(AIMaterial material) {
        AIString alphaModeString = AIString.calloc();
        int result = Assimp.aiGetMaterialString(material, Assimp.AI_MATKEY_GLTF_ALPHAMODE, Assimp.aiTextureType_NONE, 0, alphaModeString);
        
        if (result == Assimp.aiReturn_SUCCESS) {
            return alphaModeString.dataString(); // Possible values: "OPAQUE", "BLEND", "MASK"
        } else {
            return "OPAQUE"; // Default if not specified
        }
    }


    @SuppressWarnings("resource")
    public void loadModel() {
        List<XDIOPayload> xdioPayload = new ArrayList<>();
        List<XTVOPayload> xtvoPayload = new ArrayList<>();
        //List<TNOJPayload> tnoj = loadJoints((float) spinner.getValue(), scene);
        List<TNOJPayload> tnoj = loadJoints(1, scene);
        List<HSEMEntry> hsemPayload = new ArrayList<>();
                  
        Map<Short, Short> previousBoneMapping = new HashMap<>(); 
        
        int materialId = -1;
        
        byte blendFlag = 0x00;
        byte maskFlag = 0x00;
        
        for (int i = 0; i < scene.mNumMeshes(); i++) {

           AIMesh mesh = AIMesh.create(scene.mMeshes().get(i));
           
          
           //get Material Properties
           int materialIndex = mesh.mMaterialIndex();
          
           AIMaterial material = AIMaterial.create(scene.mMaterials().get(materialIndex));
            String alphaMode = getMaterialAlphaMode(material);
              
               if ("BLEND".equalsIgnoreCase(alphaMode)) 
                   blendFlag = 0x01; // Set first byte
               if ("MASK".equalsIgnoreCase(alphaMode))
                   maskFlag = 0x01;  // Set second byte
               
           if (mesh.mMaterialIndex() != materialId) {
               hsemPayload.add(new HSEMMaterialEntry((short) 0, (short) mesh.mMaterialIndex()));
               HashMap<Short, Short> map = new HashMap<>();

               map.put((short) 0, (short) mesh.mMaterialIndex());
               map.put((short) 2, (short) (scene.mNumMaterials() - 1));
               hsemPayload.add(new HSEMTextureEntry(map));
               materialId = mesh.mMaterialIndex();
               hsemPayload.add(new HSEM07Entry((short) 0x000F, (short) 0,(byte) blendFlag, (byte) maskFlag,(short) 0));
           }
       
            AIVector3D.Buffer mVertices = mesh.mVertices();
            AIVector3D.Buffer mNormals = mesh.mNormals();
            AIVector3D.Buffer tex0Buff = mesh.mTextureCoords(0);
            AIVector3D.Buffer tex1Buff = mesh.mTextureCoords(1);
            AIColor4D.Buffer mColor = mesh.mColors(0);
            AIFace.Buffer mFaces = mesh.mFaces();

            float biggest = 0;
            for (int j = 0; j < mesh.mNumVertices(); j++) {
                AIVector3D pos = mVertices.get(j);
                biggest = Math.max(Math.abs(pos.x()), biggest);
                biggest = Math.max(Math.abs(pos.y()), biggest);
                biggest = Math.max(Math.abs(pos.z()), biggest);
            }
            float vertexScale = 32767f / biggest;

            short offsetCounter = 0;

            //float scale = (float) spinner.getValue();
            float scale = 1;

            XTVOAttribute vertexAttrib = new XTVOAttribute(XTVORegisterType.POSITION, offsetCounter, (byte) 3,
                                                           XTVOValueType.SHORT,
                                                           1.0f / (vertexScale / scale));
            offsetCounter += 6;
            XTVOAttribute normalAttrib = new XTVOAttribute(XTVORegisterType.NORMAL, offsetCounter, (byte) 3,
                                                           XTVOValueType.BYTE, 1.0f / 127f);
            offsetCounter += mNormals != null ? 3 : 0;

            XTVOAttribute colorAttrib = new XTVOAttribute(XTVORegisterType.COLOR, offsetCounter, (byte) 4,
                                                          XTVOValueType.UBYTE, 1.0f / 255f);
            offsetCounter += mColor != null ? 4 : 0;

            XTVOAttribute idxAttrib = new XTVOAttribute(XTVORegisterType.IDX, offsetCounter, (byte) 4,
                                                        XTVOValueType.UBYTE, 1.0f / 255f);

            offsetCounter += mesh.mNumBones() != 0 ? 4 : 0;
            XTVOAttribute wgtAttrib = new XTVOAttribute(XTVORegisterType.WEIGHT, offsetCounter, (byte) 4,
                                                        XTVOValueType.UBYTE, 1.0f / 255f);
            offsetCounter += mesh.mNumBones() != 0 ? 4 : 0;

            XTVOAttribute tex0Attrib = new XTVOAttribute(XTVORegisterType.TEXTURE0, offsetCounter, (byte) 2,
                                                         XTVOValueType.FLOAT, 1.0f);
            offsetCounter += tex0Buff != null ? 8 : 0;

            XTVOAttribute tex1Attrib = new XTVOAttribute(XTVORegisterType.TEXTURE1, offsetCounter, (byte) 2,
                                                         XTVOValueType.FLOAT, 1.0f);

            List<XTVOAttribute> attribList = new ArrayList<>();
            attribList.add(vertexAttrib);
            attribList.add(mNormals != null ? normalAttrib : null);
            attribList.add(mColor != null ? colorAttrib : null);
            attribList.add(mesh.mNumBones() != 0 ? idxAttrib : null);
            attribList.add(mesh.mNumBones() != 0 ? wgtAttrib : null);
            attribList.add(tex0Buff != null ? tex0Attrib : null);
            attribList.add(tex1Buff != null ? tex1Attrib : null);
            attribList.removeIf(Objects::isNull);

            List<SortedMap<XTVOAttribute, List<Number>>> vertices = new ArrayList<>();

            for (int j = 0; j < mesh.mNumVertices(); j++) {
                SortedMap<XTVOAttribute, List<Number>> vertex = new TreeMap<>();

                AIVector3D pos = mVertices.get(j);
                vertex.put(vertexAttrib, List.of(pos.x() * vertexScale, pos.y() * vertexScale, pos.z() * vertexScale));

                if (mNormals != null) {
                    AIVector3D normal = mNormals.get(j);
                    vertex.put(normalAttrib, List.of(normal.x() * 127.0f, normal.y() * 127.0f, normal.z() * 127.0f));
                }
                if (mColor != null) {
                    AIColor4D color = mColor.get(j);
                    vertex.put(colorAttrib,
                               List.of(color.r() * 255f, color.g() * 255f, color.b() * 255f, color.a() * 255f));
                }
                if (tex0Buff != null) {
                    AIVector3D tex0 = tex0Buff.get(j);
                    vertex.put(tex0Attrib, List.of(tex0.x(), tex0.y()));
                }
                if (tex1Buff != null) {
                    AIVector3D tex1 = tex1Buff.get(j);
                    vertex.put(tex1Attrib, List.of(tex1.x(), tex1.y()));
                }

                vertices.add(vertex);
            }

            Map<Short, Short> boneMapping = processBoneMappingAndWeights(mesh, vertices, idxAttrib, wgtAttrib, tnoj);
            Map<Short, Short> changedBoneMapping = new HashMap<>();

            for (Map.Entry<Short, Short> entry : boneMapping.entrySet()) {
                if (!entry.getValue().equals(previousBoneMapping.get(entry.getKey()))) {
                    changedBoneMapping.put(entry.getKey(), entry.getValue());
                }
            }

            // Update previous bone mapping for next iteration
            previousBoneMapping.clear();
            previousBoneMapping.putAll(boneMapping);
        
            if (!changedBoneMapping.isEmpty()) {
                hsemPayload.add(new HSEMJointEntry(changedBoneMapping));
            }

            List<XTVOVertex> xtvoVertices = vertices.stream().map(XTVOVertex::new)
                                                  .collect(Collectors.toCollection(ArrayList::new));
           List<XDIOFace> faces = new ArrayList<>();
          for (int j = 0; j < mesh.mNumFaces(); j++) {
             AIFace face = mFaces.get(j);
            faces.add(new XDIOFace(face.mIndices().get(0), face.mIndices().get(1), face.mIndices().get(2)));
        }

          xtvoPayload.add(new XTVOPayload(null, attribList, xtvoVertices, (int) 8, (short) 0x3001,
                                         (short) 0, 0x00010309, 0x73, 0x01));
           xdioPayload.add(new XDIOPayload(null, faces, (short) 0x3001, (short) 0, 5));

           hsemPayload.add(new HSEMDrawEntry((short) 4, (short) i, (short) i, (short) 0, 0, faces.size() * 3));
   
            
       }
   
        float[] headerArray = rootKCAP.getHSEM().get(0).getHeaderData();
        HSEMPayload hsemEntry = new HSEMPayload(null, hsemPayload, -1, (short) 0, (byte) 0, (byte) 0, headerArray, 1, 0);

        Main.LOGGER.info(String.format("XDIO: %d | XTVO: %d", xdioPayload.size(), xtvoPayload.size()));
       
        rootKCAP.setHSEM(new HSEMKCAP(rootKCAP, List.of(hsemEntry)));
        rootKCAP.setXDIP(new XDIPKCAP(rootKCAP, xdioPayload));
        rootKCAP.setXTVP(new XTVPKCAP(rootKCAP, xtvoPayload));
        if (!tnoj.isEmpty())
            rootKCAP.setTNOJ(new TNOJKCAP(rootKCAP, tnoj));
        
        loadAnimations();
    }

    public void loadAnimations() {
        NormalKCAP parentKCAP = (NormalKCAP)(rootKCAP.getParent());

        for (int i = 0; i < scene.mNumAnimations(); i++) {
            AIAnimation animation = AIAnimation.create(scene.mAnimations().get(i));

            //System.out.println(animation.mName().dataString());

            int index;

            switch(animation.mName().dataString()) {
                case "idle": index = 0; break;
                case "run": index = 1; break;
                case "happy": index = 2; break;
                case "refuse": index = 3; break;
                case "sleep": index = 4; break;
                case "exhausted": index = 5; break;
                case "attack": index = 6; break;
                case "attack2": index = 7; break;
                case "attack3": index = 8; break;
                case "guard": index = 9; break;
                case "hit": index = 10; break;
                case "defeated": index = 11; break;
                case "specialattack": index = 12; break;
                case "backstep": index = 13; break;
                default: index = -1; break;
            }

            if (index > -1 && index < 14) {
                TDTMKCAP newTDTM = new TDTMKCAP(rootKCAP.getParent(), animation, jointNodes, (float)spinner.getValue());

                if (index < tdtmKCAPs.size()) {
                    tdtmKCAPs.set(index, newTDTM);
                }
            }
        }

        List<ResPayload> parentPayloads = parentKCAP.getEntries();

        for (int i = 1; i < 15; i++) {
            if (tdtmKCAPs.get(i-1) != null)
                parentPayloads.set(i, tdtmKCAPs.get(i-1));
        }
    }

    private float calculateModelScale() {
        if (jointNodes.isEmpty())
            return 1.0f;

        List<ResPayload> tnojEntries = rootKCAP.getTNOJ().getEntries();
        List<Float> floats = new ArrayList<>();

        for (AINode bla : jointNodes) {
            Optional<ResPayload> res = tnojEntries.stream()
                                                  .filter(a -> ((TNOJPayload) a).getName()
                                                                                .equals(bla.mName().dataString()))
                                                  .findFirst();

            if (!res.isPresent())
                continue;

            float[] scales = getScales(bla);
            TNOJPayload p = ((TNOJPayload) res.get());
            float xFactor = p.getXOffset() / bla.mTransformation().a4() / scales[0];
            float yFactor = p.getYOffset() / bla.mTransformation().b4() / scales[1];
            float zFactor = p.getZOffset() / bla.mTransformation().c4() / scales[2];

            if (!Float.isNaN(xFactor) && Math.abs(xFactor) != 0.0f)
                floats.add(xFactor);
            if (!Float.isNaN(yFactor) && Math.abs(yFactor) != 0.0f)
                floats.add(yFactor);
            if (!Float.isNaN(zFactor) && Math.abs(zFactor) != 0.0f)
                floats.add(zFactor);
        }

        floats.sort(Comparator.naturalOrder());
        return floats.get(floats.size() / 2);
    }

    @SuppressWarnings("resource")
    private static float[] getScales(AINode node) {
        float[] scales = { 1.0f, 1.0f, 1.0f };

        AINode currentNode = node;
        do {
            AIVector3D pos = AIVector3D.create();
            AIQuaternion quat = AIQuaternion.create();
            AIVector3D sc = AIVector3D.create();
            Assimp.aiDecomposeMatrix(currentNode.mTransformation(), sc, quat, pos);

            scales[0] *= sc.x();
            scales[1] *= sc.y();
            scales[2] *= sc.z();

            currentNode = currentNode.mParent();
        } while (isJointNode(currentNode));

        return scales;
    }

    private float srgbToLinear(float c) {
        if (c <= 0.04045f) {
            return c / 12.92f;
        } else {
            return (float) Math.pow((c + 0.055f) / 1.055f, 2.4f);
        }
    }
    
    @SuppressWarnings("resource")
    private static boolean isJointNode(AINode node) {
        if (node == null)
            return false;
        String name = node.mName().dataString();
        return name.startsWith("J_") || name.startsWith("AT_");
    }

    @SuppressWarnings("resource")
    private static List<AINode> nodeList(AINode root) {
        Deque<AINode> nodeQueue = new LinkedList<>();
        nodeQueue.add(root);

        List<AINode> nodeList = new ArrayList<>();

        while (!nodeQueue.isEmpty()) {
            AINode node = nodeQueue.removeLast();

            for (int i = 0; i < node.mNumChildren(); i++)
                nodeQueue.add(AINode.create(node.mChildren().get(i)));

            if (!node.mName().dataString().startsWith(JOINT_PREFIXES[0])
                && (!node.mName().dataString().startsWith(JOINT_PREFIXES[1])))
                continue;

            nodeList.add(node);
        }

        return nodeList;
    }

    @SuppressWarnings("resource")
    private List<TNOJPayload> loadJoints(float scale, AIScene scene) {
        List<TNOJPayload> tnojList = new ArrayList<>();
        List<String> names = new ArrayList<>();

        // Process each node to load joints and their corresponding bone matrices
        for (AINode nodes : jointNodes) {
            String name = nodes.mName().dataString();


            if (!name.startsWith(JOINT_PREFIXES[0]) && !name.startsWith(JOINT_PREFIXES[1]))
                continue;

            AINode parent = nodes.mParent();
            int parentId = parent != null ? names.indexOf(parent.mName().dataString()) : -1;

            Matrix4f matrix = new Matrix4f(aiMatrix4x4ToMatrix4f(nodes.mTransformation()));

            Vector3f translation = new Vector3f();
            Quaternionf rotation = new Quaternionf();
            Vector3f jointscale = new Vector3f();

            matrix.getTranslation(translation);
            matrix.getNormalizedRotation(rotation);
            matrix.getScale(jointscale);

            float[] translationArray = { translation.x * scale, translation.y * scale, translation.z * scale, 0.0f };
            float[] rotationArray = { rotation.x, rotation.y, rotation.z, rotation.w };
            float[] scaleArray = { 1.0f, 1.0f, 1.0f, 0.0f }; //0.0f is padding
            float[] localScaleVector = { jointscale.x, jointscale.y, jointscale.z, 0.0f };// 0.0f is padding

            Matrix4f globalTransform = computeGlobalTransform(nodes); // Get Global Transformation Matrix
            // Translation needs scale applied    
            Vector3f translation2 = new Vector3f();
            globalTransform.getTranslation(translation2);
            globalTransform.setTranslation(translation2.x * scale, translation2.y * scale, translation2.z * scale); 
                                                                              
            // Transposing from Column to Row Order, inverting and putting into the array
            float[] ibpm = matrixToArray(transposeMatrix(globalTransform.invert())); 
            
            names.add(name);

            tnojList.add(new TNOJPayload(null, parentId, name, 0, 0, ibpm, translationArray, rotationArray, scaleArray,
                                         localScaleVector));

        }

        return tnojList;
    }

    private static List<AINode> extractJointNodes(AINode root) {
        List<AINode> orderedJoints = new ArrayList<>();
        traverseNodes(root, orderedJoints);
        return orderedJoints;
    }

    private static void traverseNodes(AINode node, List<AINode> jointList) {
        if (isJointNode(node)) { // Keep only actual joint nodes
            jointList.add(node);
        }
        for (int i = 0; i < node.mNumChildren(); i++) {
            traverseNodes(AINode.create(node.mChildren().get(i)), jointList);
        }
    }

    
    private Matrix4f computeGlobalTransform(AINode node) {
        Matrix4f globalTransform = new Matrix4f(aiMatrix4x4ToMatrix4f(node.mTransformation()));
        AINode parent = node.mParent();
        while (parent != null) {
            Matrix4f parentTransform = aiMatrix4x4ToMatrix4f(parent.mTransformation());
            globalTransform.mulLocal(parentTransform);
            parent = parent.mParent();
        }
        return globalTransform;
    }

    private Matrix4f aiMatrix4x4ToMatrix4f(AIMatrix4x4 matrix) {
        return new Matrix4f(matrix.a1(), matrix.b1(), matrix.c1(), matrix.d1(), // Column 1
                            matrix.a2(), matrix.b2(), matrix.c2(), matrix.d2(), // Column 2
                            matrix.a3(), matrix.b3(), matrix.c3(), matrix.d3(), // Column 3
                            matrix.a4(), matrix.b4(), matrix.c4(), matrix.d4() // Column 4
        );
    }

    public static Matrix4f transposeMatrix(Matrix4f matrix) {
        Matrix4f result = new Matrix4f();
        result.m00(matrix.m00());
        result.m01(matrix.m10());
        result.m02(matrix.m20());
        result.m03(matrix.m30());
        result.m10(matrix.m01());
        result.m11(matrix.m11());
        result.m12(matrix.m21());
        result.m13(matrix.m31());
        result.m20(matrix.m02());
        result.m21(matrix.m12());
        result.m22(matrix.m22());
        result.m23(matrix.m32());
        result.m30(matrix.m03());
        result.m31(matrix.m13());
        result.m32(matrix.m23());
        result.m33(matrix.m33());
        return result;
    }

    private float[] matrixToArray(Matrix4fc matrix) {
        float[] array = new float[16];
        if (matrix == null) {
            return IDENTITY_MATRIX;
        }
        matrix.get(array);
        return array;
    }

    private void tnojToObj(PrintStream stream) {
        int vertexOffset = 1;

        for (ResPayload jnt : rootKCAP.getTNOJ()) {
            TNOJPayload tmp = (TNOJPayload) jnt;
            stream.println("g " + tmp.getName());
            stream.println(String.format("v %f %f %f", -tmp.getOffsetMatrix()[3], -tmp.getOffsetMatrix()[7],
                                         -tmp.getOffsetMatrix()[11]));
            stream.println("f " + vertexOffset++);
        }
    }

    @Override
    public void setSelectedFile(Object file) {
        if (file == rootKCAP)
            return;

        lblnone.setText("(None)");
        rootKCAP = null;
        if (scene != null)
            scene.close();
        scene = null;
        jointNodes.clear();

        if (file == null)
            return;

        if (!(file instanceof HSMPKCAP)) {
            Main.LOGGER.warning("Tried to select non-HSMP File in HSMPPanel.");
            return;
        }

        rootKCAP = (HSMPKCAP) file;

        tdtmKCAPs = new ArrayList<TDTMKCAP>();

        List<ResPayload> otherPayloads = rootKCAP.getParent().getEntries();

        for (int i = 1; i < 15; i++) {
            tdtmKCAPs.add(null);

            if (otherPayloads.get(i) != null) {
                if (otherPayloads.get(i).getType() == Payload.KCAP) {
                    if (((AbstractKCAP)otherPayloads.get(i)).getKCAPType() == KCAPType.TDTM) {
                        TDTMKCAP tdtm = (TDTMKCAP)otherPayloads.get(i);
                        tdtmKCAPs.set(i-1, tdtm);
                    }
                }
            }
        }
    }
}