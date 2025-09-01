import { useRef, useMemo } from "react";
import { Canvas, useFrame } from "@react-three/fiber";
import { OrbitControls, Points, Line, Text, Grid } from "@react-three/drei";
import { motion } from "framer-motion";
import * as THREE from "three";
import { Eye, Box } from "lucide-react";

// Mock VSLAM data
const generatePointCloud = (count: number) => {
  const positions = new Float32Array(count * 3);
  for (let i = 0; i < count * 3; i += 3) {
    positions[i] = (Math.random() - 0.5) * 20;      // x
    positions[i + 1] = (Math.random() - 0.5) * 10;  // y  
    positions[i + 2] = (Math.random() - 0.5) * 20;  // z
  }
  return positions;
};

const generateTrajectory = () => {
  const points = [];
  for (let i = 0; i < 50; i++) {
    const t = i / 49;
    points.push([
      Math.sin(t * Math.PI * 2) * 5,
      Math.cos(t * Math.PI) * 2,
      t * 10 - 5
    ]);
  }
  return points;
};

function PointCloud({ isScanning }: { isScanning: boolean }) {
  const pointsRef = useRef<THREE.Points>(null);
  const positions = useMemo(() => generatePointCloud(isScanning ? 2000 : 500), [isScanning]);

  useFrame((state) => {
    if (pointsRef.current && isScanning) {
      pointsRef.current.rotation.y = Math.sin(state.clock.elapsedTime * 0.1) * 0.1;
    }
  });

  return (
    <Points ref={pointsRef} positions={positions} limit={2000}>
      <pointsMaterial
        size={0.05}
        color="#00bcd4"
        transparent
        opacity={0.8}
        sizeAttenuation={true}
      />
    </Points>
  );
}

function CameraTrajectory({ isScanning }: { isScanning: boolean }) {
  const lineRef = useRef<THREE.Group>(null);
  const trajectoryPoints = useMemo(() => generateTrajectory(), []);

  useFrame((state) => {
    if (lineRef.current && isScanning) {
      lineRef.current.rotation.z = Math.sin(state.clock.elapsedTime * 0.05) * 0.02;
    }
  });

  return (
    <group ref={lineRef}>
      <Line
        points={trajectoryPoints}
        color="#ff9800"
        lineWidth={3}
        transparent
        opacity={0.9}
      />
      {/* Camera position indicators */}
      {trajectoryPoints.filter((_, i) => i % 10 === 0).map((point, index) => (
        <mesh key={index} position={point}>
          <boxGeometry args={[0.2, 0.1, 0.3]} />
          <meshBasicMaterial color="#ff9800" transparent opacity={0.7} />
        </mesh>
      ))}
    </group>
  );
}

function ScanningIndicator({ isScanning }: { isScanning: boolean }) {
  const meshRef = useRef<THREE.Mesh>(null);

  useFrame((state) => {
    if (meshRef.current && isScanning) {
      meshRef.current.rotation.y = state.clock.elapsedTime;
      const material = meshRef.current.material as THREE.MeshBasicMaterial;
      if (material) {
        material.opacity = Math.sin(state.clock.elapsedTime * 2) * 0.3 + 0.7;
      }
    }
  });

  if (!isScanning) return null;

  return (
    <mesh ref={meshRef} position={[0, 5, 0]}>
      <torusGeometry args={[2, 0.1, 8, 100]} />
      <meshBasicMaterial color="#00bcd4" transparent opacity={0.7} />
    </mesh>
  );
}

interface VSLAMVisualizationProps {
  isScanning: boolean;
  isLocalizing: boolean;
}

export function VSLAMVisualization({ isScanning, isLocalizing }: VSLAMVisualizationProps) {
  return (
    <div className="tech-panel-elevated h-full flex flex-col">
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center space-x-2">
          <Box className="h-5 w-5 text-tech-cyan" />
          <h3 className="font-semibold">3D Visualization</h3>
        </div>
        
        <div className="flex items-center space-x-4 text-xs">
          <div className="flex items-center space-x-1">
            <div className="w-2 h-2 bg-tech-cyan rounded-full" />
            <span className="text-muted-foreground">Points</span>
          </div>
          <div className="flex items-center space-x-1">
            <div className="w-2 h-2 bg-tech-amber rounded-full" />
            <span className="text-muted-foreground">Path</span>
          </div>
        </div>
      </div>

      <div className="relative flex-1 min-h-[400px] bg-surface rounded-lg overflow-hidden">
        <Canvas
          camera={{ position: [10, 10, 10], fov: 60 }}
          style={{ background: 'linear-gradient(135deg, hsl(217, 19%, 8%), hsl(217, 19%, 12%))' }}
        >
          <ambientLight intensity={0.3} />
          <pointLight position={[10, 10, 10]} intensity={0.5} color="#00bcd4" />
          <pointLight position={[-10, -10, -10]} intensity={0.3} color="#ff9800" />

          {/* Grid */}
          <Grid
            args={[20, 20]}
            position={[0, -3, 0]}
            cellSize={1}
            cellThickness={0.5}
            cellColor="#ffffff"
            sectionSize={5}
            sectionThickness={1}
            sectionColor="#00bcd4"
            fadeDistance={25}
            fadeStrength={1}
            followCamera={false}
            infiniteGrid={true}
          />

          {/* VSLAM Components */}
          <PointCloud isScanning={isScanning} />
          <CameraTrajectory isScanning={isScanning} />
          <ScanningIndicator isScanning={isScanning} />

          {/* Coordinate System */}
          <axesHelper args={[2]} />

          {/* Localization Indicator */}
          {isLocalizing && (
            <mesh position={[0, 0, 0]}>
              <sphereGeometry args={[0.5, 32, 32]} />
              <meshBasicMaterial color="#4caf50" transparent opacity={0.6} />
            </mesh>
          )}

          <OrbitControls
            enablePan={true}
            enableZoom={true}
            enableRotate={true}
            minDistance={5}
            maxDistance={50}
          />
        </Canvas>

        {/* Status Overlay */}
        {(isScanning || isLocalizing) && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            className="absolute top-4 left-4 bg-black/80 px-3 py-2 rounded-lg border border-tech/30"
          >
            <div className="space-y-1 text-sm">
              {isScanning && (
                <div className="flex items-center space-x-2 text-tech-cyan">
                  <div className="w-2 h-2 bg-tech-cyan rounded-full animate-pulse-glow" />
                  <span>Mapping Active</span>
                </div>
              )}
              {isLocalizing && (
                <div className="flex items-center space-x-2 text-tech-green">
                  <Eye className="h-3 w-3" />
                  <span>Localizing</span>
                </div>
              )}
            </div>
          </motion.div>
        )}
      </div>
    </div>
  );
}