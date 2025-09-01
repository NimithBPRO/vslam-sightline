import { useState, useEffect } from "react";
import { motion } from "framer-motion";
import { Camera, Scan, Square, Save, MapPin, Video, Settings, Zap } from "lucide-react";
import { Button } from "@/components/ui/button";
import { CameraSelector } from "./CameraSelector";
import { VideoPreview } from "./VideoPreview";
import { ControlPanel } from "./ControlPanel";
import { VSLAMVisualization } from "./VSLAMVisualization";
import { ARCoreModal } from "./ARCoreModal";
import { SavedMaps } from "./SavedMaps";

interface ScanState {
  isScanning: boolean;
  isRecording: boolean;
  isLocalizing: boolean;
}

export function VSLAMDashboard() {
  console.log("VSLAMDashboard component rendering...");
  const [selectedCamera, setSelectedCamera] = useState<string>("");
  const [arCoreEnabled, setArCoreEnabled] = useState(false);
  const [showARModal, setShowARModal] = useState(false);
  const [scanState, setScanState] = useState<ScanState>({
    isScanning: false,
    isRecording: false,
    isLocalizing: false,
  });

  const handleARCoreToggle = (enabled: boolean) => {
    if (enabled && !arCoreEnabled) {
      setShowARModal(true);
    } else {
      setArCoreEnabled(enabled);
    }
  };

  const handleScanAction = (action: string) => {
    setScanState(prev => {
      switch (action) {
        case 'start-scan':
          return { ...prev, isScanning: true };
        case 'stop-scan':
          return { ...prev, isScanning: false };
        case 'start-record':
          return { ...prev, isRecording: true };
        case 'stop-record':
          return { ...prev, isRecording: false };
        case 'localize':
          return { ...prev, isLocalizing: !prev.isLocalizing };
        default:
          return prev;
      }
    });
  };

  return (
    <div className="min-h-screen bg-background">
      {/* Header */}
      <motion.header 
        initial={{ y: -20, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        className="tech-panel-elevated border-b border-tech/20 sticky top-0 z-50 backdrop-blur-sm"
      >
        <div className="container mx-auto px-6 py-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-3">
              <div className="p-2 rounded-lg bg-gradient-primary">
                <Scan className="h-6 w-6 text-primary-foreground" />
              </div>
              <div>
                <h1 className="text-2xl font-bold text-gradient-primary">
                  VSLAM Dashboard
                </h1>
                <p className="text-sm text-muted-foreground">
                  Visual Simultaneous Localization and Mapping
                </p>
              </div>
            </div>
            
            <div className="flex items-center space-x-4">
              <div className="flex items-center space-x-2">
                <span className="text-sm text-muted-foreground">ARCore</span>
                <Button
                  variant={arCoreEnabled ? "default" : "outline"}
                  size="sm"
                  onClick={() => handleARCoreToggle(!arCoreEnabled)}
                  className={arCoreEnabled ? "glow-primary" : ""}
                >
                  <Zap className="h-4 w-4 mr-2" />
                  {arCoreEnabled ? "Enabled" : "Disabled"}
                </Button>
              </div>
              
              <Button variant="outline" size="sm">
                <Settings className="h-4 w-4 mr-2" />
                Settings
              </Button>
            </div>
          </div>
        </div>
      </motion.header>

      {/* Main Content */}
      <div className="container mx-auto p-6">
        <div className="grid grid-cols-1 lg:grid-cols-4 gap-6 h-[calc(100vh-200px)]">
          
          {/* Left Control Panel */}
          <motion.div 
            initial={{ x: -20, opacity: 0 }}
            animate={{ x: 0, opacity: 1 }}
            transition={{ delay: 0.1 }}
            className="lg:col-span-1 space-y-6"
          >
            <CameraSelector 
              selectedCamera={selectedCamera}
              onCameraSelect={setSelectedCamera}
            />
            
            <ControlPanel 
              scanState={scanState}
              onScanAction={handleScanAction}
              arCoreEnabled={arCoreEnabled}
            />
          </motion.div>

          {/* Center Content */}
          <motion.div 
            initial={{ y: 20, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            transition={{ delay: 0.2 }}
            className="lg:col-span-2 space-y-6"
          >
            <VideoPreview 
              selectedCamera={selectedCamera}
              isScanning={scanState.isScanning}
              arCoreEnabled={arCoreEnabled}
            />
          </motion.div>

          {/* Right Visualization */}
          <motion.div 
            initial={{ x: 20, opacity: 0 }}
            animate={{ x: 0, opacity: 1 }}
            transition={{ delay: 0.3 }}
            className="lg:col-span-1"
          >
            <VSLAMVisualization 
              isScanning={scanState.isScanning}
              isLocalizing={scanState.isLocalizing}
            />
          </motion.div>
        </div>

        {/* Footer with Saved Maps */}
        <motion.div
          initial={{ y: 20, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          transition={{ delay: 0.4 }}
          className="mt-8"
        >
          <SavedMaps />
        </motion.div>
      </div>

      {/* ARCore Modal */}
      <ARCoreModal 
        isOpen={showARModal}
        onClose={() => setShowARModal(false)}
        onPermissionsGranted={() => {
          setArCoreEnabled(true);
          setShowARModal(false);
        }}
      />
    </div>
  );
}