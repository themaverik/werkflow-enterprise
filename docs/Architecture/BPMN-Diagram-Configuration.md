# BPMN Diagram Configuration and Troubleshooting

## Overview

This document explains the Flowable process diagram configuration in the Werkflow platform and provides guidance on handling BPMN files with or without graphic information.

## Issue Background

### The Problem

When deploying BPMN process definitions, Flowable's `DefaultProcessDiagramGenerator` attempts to create visual diagrams during deployment. This process requires graphic information (coordinates, bounds, etc.) for each BPMN element, which is stored in the `bpmndi:BPMNDiagram` section of BPMN files.

If BPMN files lack this graphic information, the diagram generator throws a `NullPointerException`:

```
java.lang.NullPointerException: Cannot invoke "org.flowable.bpmn.model.GraphicInfo.getX()" because "flowNodeGraphicInfo" is null
at org.flowable.image.impl.DefaultProcessDiagramGenerator.initProcessDiagramCanvas(DefaultProcessDiagramGenerator.java:984)
```

### Why This Happens

BPMN files can be created in two ways:

1. **Graphical Modeling Tools**: Tools like Flowable Modeler, Camunda Modeler, or bpmn.io automatically generate the `bpmndi:BPMNDiagram` section with complete graphic information
2. **Programmatic/Manual Creation**: BPMN files created by hand or programmatically often lack the graphic information section, containing only the process logic

## Solution Implemented

### Configuration Changes

We've disabled automatic diagram generation during deployment to prevent this error while maintaining full process execution functionality.

**File**: `/services/engine/src/main/java/com/werkflow/engine/config/FlowableConfig.java`

```java
@Bean
public EngineConfigurationConfigurer<SpringProcessEngineConfiguration> processEngineConfigurer() {
    return engineConfiguration -> {
        // Disable automatic diagram generation during deployment
        engineConfiguration.setCreateDiagramOnDeploy(false);

        // Enable detailed validation
        engineConfiguration.setEnableSafeBpmnXml(true);

        // Set font names for future diagram generation
        engineConfiguration.setActivityFontName("Arial");
        engineConfiguration.setLabelFontName("Arial");
        engineConfiguration.setAnnotationFontName("Arial");
    };
}
```

**File**: `/services/engine/src/main/resources/application.yml`

```yaml
flowable:
  # Diagram generation - disabled by default
  create-diagram-on-deploy: ${FLOWABLE_CREATE_DIAGRAM_ON_DEPLOY:false}
```

### Environment Variable Control

You can override this behavior using the environment variable:

```bash
# Enable diagram generation (only if all BPMN files have graphic info)
FLOWABLE_CREATE_DIAGRAM_ON_DEPLOY=true

# Disable diagram generation (default, safe for all BPMN files)
FLOWABLE_CREATE_DIAGRAM_ON_DEPLOY=false
```

## Creating BPMN Files

### Option 1: Files Without Graphic Information (Current Approach)

**Pros**:
- Simpler to create and maintain
- Smaller file size
- Focus on process logic
- No diagram generation errors

**Cons**:
- No automatic visual diagrams
- Requires separate visualization if needed

**Example Structure**:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:flowable="http://flowable.org/bpmn"
             targetNamespace="http://www.werkflow.com/bpmn">

  <process id="myProcess" name="My Process" isExecutable="true">
    <startEvent id="start" name="Start"/>
    <userTask id="task1" name="Review Task"/>
    <endEvent id="end" name="End"/>

    <sequenceFlow id="flow1" sourceRef="start" targetRef="task1"/>
    <sequenceFlow id="flow2" sourceRef="task1" targetRef="end"/>
  </process>

  <!-- NO bpmndi:BPMNDiagram section -->
</definitions>
```

### Option 2: Files With Complete Graphic Information

**Pros**:
- Automatic visual diagrams
- Better documentation
- Visual process monitoring

**Cons**:
- More complex to maintain
- Larger file size
- Requires graphic coordinates

**Example Structure**:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:flowable="http://flowable.org/bpmn"
             xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
             xmlns:omgdc="http://www.omg.org/spec/DD/20100524/DC"
             xmlns:omgdi="http://www.omg.org/spec/DD/20100524/DI"
             targetNamespace="http://www.werkflow.com/bpmn">

  <process id="myProcess" name="My Process" isExecutable="true">
    <startEvent id="start" name="Start"/>
    <userTask id="task1" name="Review Task"/>
    <endEvent id="end" name="End"/>

    <sequenceFlow id="flow1" sourceRef="start" targetRef="task1"/>
    <sequenceFlow id="flow2" sourceRef="task1" targetRef="end"/>
  </process>

  <!-- Complete graphic information -->
  <bpmndi:BPMNDiagram id="BPMNDiagram_myProcess">
    <bpmndi:BPMNPlane bpmnElement="myProcess" id="BPMNPlane_myProcess">
      <bpmndi:BPMNShape bpmnElement="start" id="BPMNShape_start">
        <omgdc:Bounds height="30.0" width="30.0" x="100.0" y="163.0"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape bpmnElement="task1" id="BPMNShape_task1">
        <omgdc:Bounds height="80.0" width="100.0" x="200.0" y="138.0"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape bpmnElement="end" id="BPMNShape_end">
        <omgdc:Bounds height="28.0" width="28.0" x="400.0" y="164.0"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge bpmnElement="flow1" id="BPMNEdge_flow1">
        <omgdi:waypoint x="130.0" y="178.0"/>
        <omgdi:waypoint x="200.0" y="178.0"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge bpmnElement="flow2" id="BPMNEdge_flow2">
        <omgdi:waypoint x="300.0" y="178.0"/>
        <omgdi:waypoint x="400.0" y="178.0"/>
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</definitions>
```

## Recommended Approach

### For New BPMN Files

1. **Development Phase**: Create BPMN files programmatically without graphic information for faster development
2. **Production Phase**: If visual diagrams are needed, use a BPMN modeler to add graphic information

### Using BPMN Modeling Tools

We recommend using one of these tools to create BPMN files with complete graphic information:

1. **Flowable Modeler** (included with Flowable)
   - Web-based
   - Integrated with Flowable
   - Export directly deployable files

2. **Camunda Modeler** (free, open-source)
   - Desktop application
   - Excellent BPMN 2.0 support
   - Works with Flowable

3. **bpmn.io** (open-source library)
   - Can be embedded in applications
   - Good for custom tools

## Verification

### Check if BPMN File Has Graphic Information

Look for the `bpmndi:BPMNDiagram` section in your BPMN file:

```bash
# Search for diagram information in a BPMN file
grep -q "bpmndi:BPMNDiagram" your-process.bpmn20.xml && echo "Has graphics" || echo "No graphics"
```

### Verify All BPMN Files

```bash
# Check all BPMN files in resources
find services/*/src/main/resources/processes -name "*.bpmn20.xml" -exec sh -c \
  'echo -n "{}: "; grep -q "bpmndi:BPMNDiagram" "{}" && echo "✓ Has graphics" || echo "✗ Missing graphics"' \;
```

## Impact Assessment

### Process Execution

**No Impact**: Process execution is completely unaffected by the presence or absence of graphic information. All process definitions deploy and execute normally.

### Visual Monitoring

**Partial Impact**:
- Processes without graphic info: No automatic visual diagrams
- Processes with graphic info: Visual diagrams available (if `create-diagram-on-deploy` is enabled)

### Workarounds for Visualization

If you need to visualize processes without graphic information:

1. **Export to Modeler**: Import the BPMN file into a modeler tool, which will auto-layout the diagram
2. **Custom Visualization**: Use the Flowable REST API to query process structure and build custom visualizations
3. **Runtime Monitoring**: Use Flowable Admin UI to view running process instances

## Future Enhancements

Potential improvements for better BPMN diagram support:

1. **Gradle/Maven Plugin**: Auto-generate graphic information during build
2. **Custom Diagram Generator**: Create a null-safe diagram generator that uses default layouts
3. **Hybrid Approach**: Store graphic information separately from BPMN logic
4. **BPMN Linting**: Pre-deployment validation to warn about missing graphics

## Troubleshooting

### Error: NullPointerException in DefaultProcessDiagramGenerator

**Cause**: `create-diagram-on-deploy` is set to `true` but BPMN files lack graphic information

**Solution**:
```bash
# Set environment variable
export FLOWABLE_CREATE_DIAGRAM_ON_DEPLOY=false

# Or update application.yml
flowable:
  create-diagram-on-deploy: false
```

### Error: Process Definition Not Found

**Cause**: Unrelated to diagram configuration; check process deployment

**Solution**: Verify BPMN files are in the correct location:
- `/src/main/resources/processes/` for auto-deployment
- Check application logs for deployment errors

## References

- [Flowable Documentation](https://www.flowable.com/open-source/docs/)
- [BPMN 2.0 Specification](https://www.omg.org/spec/BPMN/2.0/)
- [Camunda Modeler](https://camunda.com/download/modeler/)
- [bpmn.io](https://bpmn.io/)

## Related Files

- `/services/engine/src/main/java/com/werkflow/engine/config/FlowableConfig.java`
- `/services/engine/src/main/resources/application.yml`
- `/services/*/src/main/resources/processes/*.bpmn20.xml`
