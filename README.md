
# SPF IPv6 Router Visualizer

A Java Swing GUI application that visualizes the **Shortest Path First (SPF) Dijkstra algorithm** in action across an IPv6 network topology. This interactive simulator demonstrates how routers discover neighbors, exchange routing information, and compute optimal paths.

<img width="761" height="574" alt="image" src="https://github.com/user-attachments/assets/18bdfa3b-edb4-4032-ad6d-951ef8c74cc0" />
## Overview

The SPF IPv6 Router Visualizer is an educational tool that brings core OSPF routing concepts to life through real-time visualization. Watch as three routers initialize, exchange network information, and the Dijkstra algorithm calculates the shortest paths across the network.

## Features

### 🌐 **Three-Router Topology**
- **R1 (Sender)**: Source node initiating SPF computation
- **R2 (Receiver)**: Network node with dual connections
- **R3 (Relay)**: Intermediate node connecting R1 and R2
- Interactive visual representation with link costs

### 📊 **Live Packet Animation**
- Animated packets flowing between routers during initialization
- Color-coded packets for different types of traffic
- Real-time visualization of network communication

### 📈 **Network Information Display**
- **LSA Database (LSDB)**: View advertised link-state advertisements from all routers
- **Neighbor Table**: Monitor router adjacency state and IPv6 addresses
- **Routing Table**: Display computed routes with costs and next-hop information
- **Packet Counters**: Track HELLO, LSA, DBD, and ACK packets

### 🔬 **Dijkstra Algorithm Simulation**
- Step-by-step execution of the shortest path algorithm
- Distance vector updates displayed in real-time
- Final routing paths highlighted on the topology
- Detailed event logging of all operations

### 🎨 **Clean, Modern UI**
- Professional color scheme with status indicators
- Zebra-striped tables for readability
- Real-time event log with timestamps
- Responsive layout with collapsible panels

## How It Works

### Workflow

1. **Initialize R2 (Receiver)**
   - R2 starts with its own LSA
   - Address: `2001:db8::2`
   - Link to R1 (cost 10) and R3 (cost 3)

2. **Initialize R1 (Sender)**
   - R1 starts with its own LSA
   - Address: `2001:db8::1`
   - Link to R2 (cost 10) and R3 (cost 5)

3. **Initialize R3 (Relay)**
   - R3 connects R1 and R2
   - Address: `2001:db8::3`
   - Link to R1 (cost 5) and R2 (cost 3)

4. **Run SPF Calculation**
   - Executes Dijkstra algorithm from R1
   - Computes optimal paths to all destinations
   - Result: Route to R2 via R3 (cost 8) is preferred over direct route (cost 10)

### Network Topology

```
R1 (2001:db8::1)
├─ Direct to R2: cost 10
├─ Direct to R3: cost 5
└─ Via R3 to R2: cost 8 ★ (shortest path)

R3 (2001:db8::3)
├─ Direct to R1: cost 5
├─ Direct to R2: cost 3

R2 (2001:db8::2)
├─ Direct to R1: cost 10
├─ Direct to R3: cost 3
```

## Controls

| Button | Action |
|--------|--------|
| **▶ Start R2** | Initialize R2 router (must start first) |
| **▶ Start R1** | Initialize R1 router (requires R2 running) |
| **▶ Start R3** | Initialize R3 router (requires R1 running) |
| **⊕ Run SPF Calculation** | Execute Dijkstra shortest path algorithm (requires all routers running) |
| **↺ Reset** | Clear all state and return to initial state |

## Technical Details

### Requirements
- Java 8 or higher
- Swing GUI framework (included with Java)

### Architecture

**Main Components:**
- `TopologyPanel`: Renders network topology with routers and links
- `Packet`: Animates data transfer between routers
- `DefaultTableModel`: Displays LSDB, neighbor, and routing tables
- Multi-threaded simulation with synchronized packet list

**Key Classes:**
- `TopologyPanel`: Custom JPanel for network visualization
- `Packet`: Inner class representing in-flight network packets

### Threading Model
- Event initialization runs in background threads
- Packet animation runs on a 30ms timer
- UI updates are dispatched to Swing Event Dispatch Thread

## Building and Running

### Compile
```bash
javac SPF_IPv6_Router_Visualizer.java
```

### Run
```bash
java SPF_IPv6_Router_Visualizer
```

### Quick Start
1. Click **▶ Start R2** to initialize the receiver
2. Click **▶ Start R1** to initialize the sender
3. Click **▶ Start R3** to add the relay node
4. Click **⊕ Run SPF Calculation** to compute shortest paths
5. Observe the topology, routing table, and event log

## What You'll Learn

✓ How OSPF neighbor discovery works  
✓ Link-State Advertisement (LSA) propagation  
✓ Dijkstra's shortest path algorithm in action  
✓ IPv6 routing and addressing basics  
✓ Network topology representation  
✓ Dynamic routing table computation  

## Educational Use

This visualizer is ideal for:
- Network engineering students learning OSPF protocols
- Network administrators understanding routing algorithm fundamentals
- Computer science courses on graph algorithms and networking
- Interactive demonstrations of the Dijkstra algorithm

## Customization

You can extend this visualizer by:
- Adding more routers to the topology
- Modifying link costs
- Simulating network failures (link downs)
- Implementing different routing algorithms (OSPF, IS-IS, etc.)
- Adding support for MPLS or traffic engineering


