import { useState, useEffect } from "react";
import { Camera, ChevronDown } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

interface CameraDevice {
  deviceId: string;
  label: string;
  kind: MediaDeviceKind;
}

interface CameraSelectorProps {
  selectedCamera: string;
  onCameraSelect: (deviceId: string) => void;
}

export function CameraSelector({ selectedCamera, onCameraSelect }: CameraSelectorProps) {
  const [cameras, setCameras] = useState<CameraDevice[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getCameraDevices();
  }, []);

  const getCameraDevices = async () => {
    try {
      // Request permission first
      await navigator.mediaDevices.getUserMedia({ video: true });
      
      const devices = await navigator.mediaDevices.enumerateDevices();
      const videoDevices = devices
        .filter(device => device.kind === 'videoinput')
        .map((device, index) => ({
          deviceId: device.deviceId,
          label: device.label || `Camera ${index + 1}`,
          kind: device.kind,
        }));

      setCameras(videoDevices);
      
      // Select first camera by default
      if (videoDevices.length > 0 && !selectedCamera) {
        onCameraSelect(videoDevices[0].deviceId);
      }
    } catch (error) {
      console.error('Error accessing camera devices:', error);
    } finally {
      setLoading(false);
    }
  };

  const selectedCameraLabel = cameras.find(cam => cam.deviceId === selectedCamera)?.label || "Select Camera";

  return (
    <div className="tech-panel space-y-4">
      <div className="flex items-center space-x-2">
        <Camera className="h-5 w-5 text-tech-cyan" />
        <h3 className="font-semibold text-foreground">Camera Source</h3>
      </div>

      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button 
            variant="outline" 
            className="w-full justify-between bg-surface/50 hover:bg-surface border-tech/30"
            disabled={loading}
          >
            <span className="truncate">
              {loading ? "Loading cameras..." : selectedCameraLabel}
            </span>
            <ChevronDown className="h-4 w-4 opacity-50" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent className="w-full min-w-[200px] bg-surface-elevated border-tech/30">
          {cameras.map((camera) => (
            <DropdownMenuItem
              key={camera.deviceId}
              onClick={() => onCameraSelect(camera.deviceId)}
              className="cursor-pointer hover:bg-surface focus:bg-surface"
            >
              <Camera className="h-4 w-4 mr-2" />
              <span className="truncate">{camera.label}</span>
            </DropdownMenuItem>
          ))}
          {cameras.length === 0 && !loading && (
            <DropdownMenuItem disabled>
              No cameras found
            </DropdownMenuItem>
          )}
        </DropdownMenuContent>
      </DropdownMenu>

      <div className="text-xs text-muted-foreground">
        {cameras.length} camera{cameras.length !== 1 ? 's' : ''} detected
      </div>
    </div>
  );
}