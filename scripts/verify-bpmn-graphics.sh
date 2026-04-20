#!/bin/bash

# BPMN Graphics Verification Script
# This script checks all BPMN files in the project to identify which ones
# have complete, partial, or missing graphic information

set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo "=========================================="
echo "BPMN Graphics Information Report"
echo "=========================================="
echo ""

# Find project root (assume script is in scripts/ directory)
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_ROOT"

# Counters
total_files=0
has_graphics=0
partial_graphics=0
no_graphics=0

# Find all BPMN files
echo "Scanning for BPMN files..."
echo ""

while IFS= read -r bpmn_file; do
    total_files=$((total_files + 1))

    # Get relative path for display
    rel_path="${bpmn_file#$PROJECT_ROOT/}"

    # Check for bpmndi:BPMNDiagram section
    if grep -q "bpmndi:BPMNDiagram" "$bpmn_file"; then
        # Count number of process elements
        process_count=$(grep -c "<startEvent\|<endEvent\|<userTask\|<serviceTask\|<exclusiveGateway\|<parallelGateway" "$bpmn_file" || echo "0")

        # Count number of shape elements
        shape_count=$(grep -c "bpmndi:BPMNShape" "$bpmn_file" || echo "0")

        if [ "$shape_count" -ge "$process_count" ]; then
            echo -e "${GREEN}✓ COMPLETE${NC} $rel_path"
            echo "  └─ Process elements: $process_count, Diagram shapes: $shape_count"
            has_graphics=$((has_graphics + 1))
        else
            echo -e "${YELLOW}⚠ PARTIAL${NC}  $rel_path"
            echo "  └─ Process elements: $process_count, Diagram shapes: $shape_count (INCOMPLETE)"
            partial_graphics=$((partial_graphics + 1))
        fi
    else
        echo -e "${RED}✗ MISSING${NC}  $rel_path"
        no_graphics=$((no_graphics + 1))
    fi
    echo ""
done < <(find services/*/src/main/resources/processes -name "*.bpmn20.xml" 2>/dev/null)

# Summary
echo "=========================================="
echo "Summary"
echo "=========================================="
echo "Total BPMN files found: $total_files"
echo ""
echo -e "${GREEN}Complete graphics:${NC}  $has_graphics files"
echo -e "${YELLOW}Partial graphics:${NC}   $partial_graphics files"
echo -e "${RED}Missing graphics:${NC}   $no_graphics files"
echo ""

# Recommendations
if [ $no_graphics -gt 0 ] || [ $partial_graphics -gt 0 ]; then
    echo "=========================================="
    echo "Recommendations"
    echo "=========================================="

    if [ $no_graphics -gt 0 ]; then
        echo -e "${RED}WARNING:${NC} $no_graphics file(s) have no graphic information"
        echo "Action: Ensure 'create-diagram-on-deploy' is set to 'false' in application.yml"
        echo ""
    fi

    if [ $partial_graphics -gt 0 ]; then
        echo -e "${YELLOW}WARNING:${NC} $partial_graphics file(s) have incomplete graphic information"
        echo "Action: Consider regenerating graphics using a BPMN modeler tool"
        echo ""
    fi

    echo "Current configuration status:"
    if grep -q "create-diagram-on-deploy.*false" services/engine/src/main/resources/application.yml 2>/dev/null; then
        echo -e "${GREEN}✓ Diagram generation is DISABLED (safe)${NC}"
    else
        echo -e "${RED}✗ Diagram generation may be ENABLED (unsafe with missing graphics)${NC}"
        echo "  Please set 'flowable.create-diagram-on-deploy: false' in application.yml"
    fi
else
    echo "=========================================="
    echo "Status"
    echo "=========================================="
    echo -e "${GREEN}✓ All BPMN files have complete graphic information${NC}"
    echo "You may enable 'create-diagram-on-deploy' if desired"
fi

echo ""
echo "For more information, see: docs/BPMN-Diagram-Configuration.md"
echo ""
