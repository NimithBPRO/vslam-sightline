import { motion } from "framer-motion";
import { Play, Square, Save, MapPin, Video, VideoOff, Zap } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";

interface ScanState {
  isScanning: boolean;
  isRecording: boolean;
  isLocalizing: boolean;
}

interface ControlPanelProps {
  scanState: ScanState;
  onScanAction: (action: string) => void;
  arCoreEnabled: boolean;
}

export function ControlPanel({ scanState, onScanAction, arCoreEnabled }: ControlPanelProps) {
  const { isScanning, isRecording, isLocalizing } = scanState;

  return (
    <div className="tech-panel space-y-6">
      <div className="flex items-center space-x-2">
        <Zap className="h-5 w-5 text-tech-cyan" />
        <h3 className="font-semibold text-foreground">Control Panel</h3>
      </div>

      {/* Main Controls */}
      <div className="space-y-3">
        <motion.div whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.98 }}>
          <Button
            onClick={() => onScanAction(isScanning ? 'stop-scan' : 'start-scan')}
            className={`w-full ${isScanning 
              ? 'bg-destructive hover:bg-destructive/90 shadow-glow' 
              : 'bg-gradient-primary hover:opacity-90 shadow-tech'
            }`}
            size="lg"
          >
            {isScanning ? (
              <>
                <Square className="h-5 w-5 mr-2" />
                Stop Scan
              </>
            ) : (
              <>
                <Play className="h-5 w-5 mr-2" />
                Start Scan
              </>
            )}
          </Button>
        </motion.div>

        <motion.div whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.98 }}>
          <Button
            onClick={() => onScanAction('save-map')}
            variant="outline"
            className="w-full border-tech/50 hover:border-tech hover:bg-surface/50"
            disabled={!isScanning}
          >
            <Save className="h-4 w-4 mr-2" />
            Save Map
          </Button>
        </motion.div>

        <motion.div whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.98 }}>
          <Button
            onClick={() => onScanAction('localize')}
            variant={isLocalizing ? "default" : "outline"}
            className={`w-full ${isLocalizing 
              ? 'bg-gradient-accent hover:opacity-90 text-black shadow-glow' 
              : 'border-tech/50 hover:border-tech hover:bg-surface/50'
            }`}
          >
            <MapPin className="h-4 w-4 mr-2" />
            {isLocalizing ? 'Stop Localize' : 'Localize'}
          </Button>
        </motion.div>
      </div>

      <Separator className="bg-border/50" />

      {/* Recording Controls */}
      <div className="space-y-3">
        <h4 className="text-sm font-medium text-muted-foreground">Recording</h4>
        
        <motion.div whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.98 }}>
          <Button
            onClick={() => onScanAction(isRecording ? 'stop-record' : 'start-record')}
            variant={isRecording ? "destructive" : "outline"}
            className={`w-full ${!isRecording 
              ? 'border-tech/50 hover:border-tech hover:bg-surface/50' 
              : 'shadow-glow'
            }`}
          >
            {isRecording ? (
              <>
                <VideoOff className="h-4 w-4 mr-2" />
                Stop Recording
              </>
            ) : (
              <>
                <Video className="h-4 w-4 mr-2" />
                Record Video
              </>
            )}
          </Button>
        </motion.div>
      </div>

      <Separator className="bg-border/50" />

      {/* Status Indicators */}
      <div className="space-y-2">
        <h4 className="text-sm font-medium text-muted-foreground">Status</h4>
        
        <div className="space-y-2 text-sm">
          <div className="flex justify-between items-center">
            <span>ARCore:</span>
            <span className={arCoreEnabled ? 'text-tech-green' : 'text-muted-foreground'}>
              {arCoreEnabled ? 'Enabled' : 'Disabled'}
            </span>
          </div>
          
          <div className="flex justify-between items-center">
            <span>Scanning:</span>
            <div className="flex items-center space-x-1">
              {isScanning && <div className="w-2 h-2 bg-tech-cyan rounded-full animate-pulse-glow" />}
              <span className={isScanning ? 'text-tech-cyan' : 'text-muted-foreground'}>
                {isScanning ? 'Active' : 'Idle'}
              </span>
            </div>
          </div>
          
          <div className="flex justify-between items-center">
            <span>Recording:</span>
            <div className="flex items-center space-x-1">
              {isRecording && <div className="w-2 h-2 bg-tech-red rounded-full animate-pulse-glow" />}
              <span className={isRecording ? 'text-tech-red' : 'text-muted-foreground'}>
                {isRecording ? 'Recording' : 'Stopped'}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}