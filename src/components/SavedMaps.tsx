import { useState } from "react";
import { motion } from "framer-motion";
import { Map, Calendar, FileText, Download, Trash2, Eye } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";

interface SavedMap {
  id: string;
  name: string;
  date: string;
  size: string;
  points: number;
  status: 'complete' | 'processing' | 'error';
}

// Mock data
const mockMaps: SavedMap[] = [
  {
    id: '1',
    name: 'Office Scan Alpha',
    date: '2024-01-15T10:30:00Z',
    size: '12.4 MB',
    points: 15420,
    status: 'complete'
  },
  {
    id: '2',
    name: 'Warehouse Beta',
    date: '2024-01-14T15:45:00Z',
    size: '8.7 MB',
    points: 9876,
    status: 'complete'
  },
  {
    id: '3',
    name: 'Lab Environment',
    date: '2024-01-13T09:15:00Z',
    size: '6.2 MB',
    points: 7234,
    status: 'processing'
  },
  {
    id: '4',
    name: 'Corridor Mapping',
    date: '2024-01-12T14:20:00Z',
    size: '4.1 MB',
    points: 4567,
    status: 'error'
  }
];

export function SavedMaps() {
  const [maps] = useState<SavedMap[]>(mockMaps);

  const formatDate = (dateString: string) => {
    return new Intl.DateTimeFormat('en-US', {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    }).format(new Date(dateString));
  };

  const getStatusColor = (status: SavedMap['status']) => {
    switch (status) {
      case 'complete':
        return 'bg-tech-green/20 text-tech-green border-tech-green/30';
      case 'processing':
        return 'bg-tech-amber/20 text-tech-amber border-tech-amber/30';
      case 'error':
        return 'bg-tech-red/20 text-tech-red border-tech-red/30';
      default:
        return 'bg-muted text-muted-foreground';
    }
  };

  const handleMapAction = (mapId: string, action: string) => {
    console.log(`${action} map:`, mapId);
    // Placeholder for backend integration
  };

  return (
    <div className="tech-panel">
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center space-x-2">
          <Map className="h-5 w-5 text-tech-cyan" />
          <h3 className="font-semibold text-foreground">Saved Maps</h3>
          <Badge variant="secondary" className="text-xs">
            {maps.length}
          </Badge>
        </div>
        
        <Button variant="outline" size="sm" className="border-tech/30 hover:border-tech">
          <FileText className="h-4 w-4 mr-2" />
          Export All
        </Button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        {maps.map((map, index) => (
          <motion.div
            key={map.id}
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: index * 0.1 }}
            whileHover={{ scale: 1.02, transition: { duration: 0.2 } }}
            className="tech-panel bg-surface/50 hover:bg-surface transition-colors cursor-pointer group"
          >
            <div className="space-y-3">
              {/* Header */}
              <div className="flex items-start justify-between">
                <div className="flex-1 min-w-0">
                  <h4 className="font-medium text-sm truncate group-hover:text-tech-cyan transition-colors">
                    {map.name}
                  </h4>
                  <div className="flex items-center space-x-1 mt-1">
                    <Calendar className="h-3 w-3 text-muted-foreground" />
                    <span className="text-xs text-muted-foreground">
                      {formatDate(map.date)}
                    </span>
                  </div>
                </div>
                
                <Badge className={`text-xs ${getStatusColor(map.status)}`}>
                  {map.status}
                </Badge>
              </div>

              {/* Stats */}
              <div className="space-y-2 text-xs text-muted-foreground">
                <div className="flex justify-between">
                  <span>Points:</span>
                  <span className="font-mono">{map.points.toLocaleString()}</span>
                </div>
                <div className="flex justify-between">
                  <span>Size:</span>
                  <span className="font-mono">{map.size}</span>
                </div>
              </div>

              {/* Actions */}
              <div className="flex justify-between pt-2 border-t border-border/30 opacity-0 group-hover:opacity-100 transition-opacity">
                <Button
                  size="sm"
                  variant="ghost"
                  className="h-6 px-2 text-xs hover:text-tech-cyan"
                  onClick={(e) => {
                    e.stopPropagation();
                    handleMapAction(map.id, 'view');
                  }}
                >
                  <Eye className="h-3 w-3" />
                </Button>
                
                <Button
                  size="sm"
                  variant="ghost"
                  className="h-6 px-2 text-xs hover:text-tech-green"
                  onClick={(e) => {
                    e.stopPropagation();
                    handleMapAction(map.id, 'download');
                  }}
                  disabled={map.status !== 'complete'}
                >
                  <Download className="h-3 w-3" />
                </Button>
                
                <Button
                  size="sm"
                  variant="ghost"
                  className="h-6 px-2 text-xs hover:text-destructive"
                  onClick={(e) => {
                    e.stopPropagation();
                    handleMapAction(map.id, 'delete');
                  }}
                >
                  <Trash2 className="h-3 w-3" />
                </Button>
              </div>
            </div>
          </motion.div>
        ))}

        {/* Add New Map Placeholder */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: maps.length * 0.1 }}
          className="tech-panel bg-surface/20 border-dashed border-2 border-tech/30 hover:border-tech/60 transition-colors cursor-pointer flex flex-col items-center justify-center text-center min-h-[140px] group"
        >
          <div className="space-y-2">
            <div className="w-8 h-8 rounded-full bg-tech/20 flex items-center justify-center group-hover:bg-tech/30 transition-colors">
              <Map className="h-4 w-4 text-tech-cyan" />
            </div>
            <div>
              <p className="text-sm font-medium text-muted-foreground group-hover:text-foreground transition-colors">
                New Scan
              </p>
              <p className="text-xs text-muted-foreground">
                Start scanning to create a new map
              </p>
            </div>
          </div>
        </motion.div>
      </div>
    </div>
  );
}