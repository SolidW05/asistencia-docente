import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { GeoJSON, MapContainer, Marker, TileLayer, ZoomControl, useMap } from 'react-leaflet'
import * as L from 'leaflet'
import type { Feature, FeatureCollection } from 'geojson'
import type { ChainBlock, Geofence, PolygonGeometry, Position } from '../types'

const FALLBACK_CENTER: L.LatLngExpression = [20.67, -103.35]

function featureFor(fence: Geofence): Feature {
  return {
    type: 'Feature',
    id: fence.id,
    properties: { geofenceId: fence.id, name: fence.name, type: fence.type, active: fence.active },
    geometry: fence.geometry,
  }
}

function featureCollection(fences: Geofence[]): FeatureCollection {
  return { type: 'FeatureCollection', features: fences.map(featureFor) }
}

function LocationMarker({ location }: { location: Position | null }) {
  if (!location) return null
  const icon = L.divIcon({ className: 'position-dot', html: '<span></span>', iconSize: [24, 24], iconAnchor: [12, 12] })
  return <Marker position={[location.latitude, location.longitude]} icon={icon} />
}

function FitMap({ fences, location, fitCampusKey }: { fences: Geofence[]; location: Position | null; fitCampusKey: number }) {
  const map = useMap()
  const lastFitCampusKey = useRef(fitCampusKey)
  useEffect(() => {
    const bounds = L.geoJSON(featureCollection(fences) as never).getBounds()
    const requestedCampusFit = fitCampusKey !== lastFitCampusKey.current
    lastFitCampusKey.current = fitCampusKey
    if (requestedCampusFit && bounds.isValid()) {
      map.fitBounds(bounds.pad(0.16), { maxZoom: 16, animate: true })
      return
    }
    if (location) {
      map.setView([location.latitude, location.longitude], Math.max(map.getZoom(), 16), { animate: true })
      return
    }
    if (bounds.isValid()) map.fitBounds(bounds.pad(0.16), { maxZoom: 16 })
  }, [fences, fitCampusKey, location, map])
  return null
}

interface GeomanMapApi {
  addControls: (options: Record<string, unknown>) => void
  removeControls: () => void
  enableGlobalEditMode: () => void
  disableGlobalEditMode: () => void
  setGlobalOptions: (options: Record<string, unknown>) => void
  setLang: (language: 'es') => void
}

type GeomanLayer = L.Layer & {
  toGeoJSON?: () => unknown
  feature?: Feature
  pm?: { enable: (options?: Record<string, unknown>) => void }
}
type GeomanEvent = L.LeafletEvent & { layer?: GeomanLayer }

function GeomanControls({
  enabled,
  selectedId,
  onGeometry,
}: {
  enabled: boolean
  selectedId: string | null
  onGeometry: (geometry: PolygonGeometry, source: 'draw' | 'edit') => void
}) {
  const map = useMap()
  useEffect(() => {
    if (!enabled) return
    const geoman = (map as L.Map & { pm?: GeomanMapApi }).pm
    if (!geoman) return
    geoman.setLang('es')
    geoman.setGlobalOptions({ allowSelfIntersection: false, snappable: true })
    geoman.addControls({
      position: 'topleft', drawPolygon: true, drawPolyline: false, drawCircle: false,
      drawCircleMarker: false, drawMarker: false, drawRectangle: false, drawText: false, editMode: false,
      dragMode: false, cutPolygon: false, removalMode: false, rotateMode: false,
    })
    const extract = (event: GeomanEvent, source: 'draw' | 'edit', target?: GeomanLayer) => {
      const layer = event.layer ?? target
      const result = layer?.toGeoJSON?.() as { geometry?: PolygonGeometry; type?: string } | undefined
      if (!result?.geometry || result.geometry.type !== 'Polygon') return
      if (source === 'edit') {
        const id = layer?.feature?.properties?.geofenceId
        if (selectedId && id !== selectedId && id !== '__draft__') return
      }
      onGeometry(result.geometry, source)
    }
    const created = (event: L.LeafletEvent) => {
      const layer = (event as GeomanEvent).layer
      if (!layer) return
      const current = layer.toGeoJSON?.() as Feature | undefined
      if (current?.geometry) layer.feature = { ...current, properties: { ...(current.properties ?? {}), geofenceId: '__draft__' } }
      layer.pm?.enable?.({ allowSelfIntersection: false })
      layer.on('pm:edit', (editEvent: L.LeafletEvent) => extract(editEvent as GeomanEvent, 'edit', layer))
      extract(event as GeomanEvent, 'draw', layer)
    }
    map.on('pm:create', created)
    return () => {
      map.off('pm:create', created)
      geoman.disableGlobalEditMode()
      geoman.removeControls()
    }
  }, [enabled, map, onGeometry, selectedId])
  return null
}

export interface MapViewProps {
  geofences: Geofence[]
  location?: Position | null
  editable?: boolean
  selectedId?: string | null
  onGeometryChange?: (geometry: PolygonGeometry, source: 'draw' | 'edit') => void
  mapKey?: string | number
  fitCampusKey?: number
}

export function MapView({ geofences, location = null, editable = false, selectedId = null, onGeometryChange, mapKey, fitCampusKey = 0 }: MapViewProps) {
  const [tilesUnavailable, setTilesUnavailable] = useState(false)
  const data = useMemo(() => featureCollection(geofences), [geofences])
  const onEachFeature = useCallback((feature: Feature, layer: L.Layer) => {
    const geofenceId = (feature.properties as { geofenceId?: string } | null)?.geofenceId
    const editableLayer = layer as GeomanLayer
    if (editable && selectedId && geofenceId === selectedId) {
      const enableSelected = () => editableLayer.pm?.enable?.({ allowSelfIntersection: false })
      if ((editableLayer as GeomanLayer & { _map?: L.Map })._map) enableSelected()
      else editableLayer.once('add', enableSelected)
      editableLayer.on('pm:edit', (event: L.LeafletEvent) => {
        const result = editableLayer.toGeoJSON?.() as Feature | undefined
        if (result?.geometry.type === 'Polygon') onGeometryChange?.(result.geometry as PolygonGeometry, 'edit')
        void event
      })
    }
  }, [editable, onGeometryChange, selectedId])
  const layerKey = useMemo(() => `${mapKey ?? 'static'}:${editable ? 'edit' : 'view'}:${selectedId ?? ''}:${geofences.map((fence) => `${fence.id}:${fence.version}:${JSON.stringify(fence.geometry)}`).join('|')}`, [editable, geofences, mapKey, selectedId])
  const style = (feature?: Feature) => {
    const properties = feature?.properties as { type?: string; active?: boolean } | undefined
    const campus = properties?.type === 'CAMPUS'
    return {
      color: properties?.active === false ? '#89979a' : campus ? '#245c50' : '#c77b31',
      weight: campus ? 3 : 2,
      opacity: 0.94,
      fillColor: campus ? '#87b9a4' : '#e7a764',
      fillOpacity: properties?.active === false ? 0.08 : 0.18,
      dashArray: properties?.active === false ? '6 6' : undefined,
    }
  }

  return (
    <div className="map-frame">
      <MapContainer key={mapKey} center={FALLBACK_CENTER} zoom={12} scrollWheelZoom zoomControl={false} className="map-canvas">
        <ZoomControl position="topright" zoomInTitle="Acercar" zoomOutTitle="Alejar" />
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          eventHandlers={{ tileerror: () => setTilesUnavailable(true), tileload: () => setTilesUnavailable(false) }}
        />
        {geofences.length > 0 && <GeoJSON key={layerKey} data={data} style={style} onEachFeature={onEachFeature} />}
        <LocationMarker location={location} />
        <FitMap fences={geofences} location={location} fitCampusKey={fitCampusKey} />
        {editable && onGeometryChange && <GeomanControls enabled={editable} selectedId={selectedId} onGeometry={onGeometryChange} />}
      </MapContainer>
      {tilesUnavailable && <div className="map-message" role="status">No se pudieron cargar las imágenes del mapa. La ubicación y el registro se consultan por separado.</div>}
      {geofences.length === 0 && <div className="map-empty-label">Sin geocercas configuradas</div>}
      {location && <div className="map-legend"><span className="legend-location" /> Tu última posición</div>}
    </div>
  )
}

export function inspectBlock(block: ChainBlock): string {
  return `${block.eventType} · ${block.serverAt}`
}
