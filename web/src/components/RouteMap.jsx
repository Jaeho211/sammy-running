import React, { useEffect, useRef, useState } from "react";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
export default function RouteMap({ route = [] }) {
  const element = useRef(null);
  const [tileError, setTileError] = useState(false);
  useEffect(() => {
    if (route.length < 2 || !element.current) return;
    setTileError(false);
    const map = L.map(element.current, { scrollWheelZoom: false });
    const tiles = L.tileLayer(
      "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
      {
        maxZoom: 19,
        attribution:
          '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
      },
    ).addTo(map);
    tiles.on("tileerror", () => setTileError(true));
    const points = route.map((p) => [p.lat, p.lng]);
    const line = L.polyline(points, { color: "#28715b", weight: 5 }).addTo(map);
    L.circleMarker(points[0], {
      radius: 7,
      color: "#fff",
      fillColor: "#28715b",
      fillOpacity: 1,
      weight: 3,
    })
      .addTo(map)
      .bindTooltip("출발");
    L.circleMarker(points.at(-1), {
      radius: 7,
      color: "#fff",
      fillColor: "#db804f",
      fillOpacity: 1,
      weight: 3,
    })
      .addTo(map)
      .bindTooltip("도착");
    map.fitBounds(line.getBounds(), { padding: [32, 32], maxZoom: 16 });
    const observer = new ResizeObserver(() => map.invalidateSize());
    observer.observe(element.current);
    return () => {
      observer.disconnect();
      map.remove();
    };
  }, [route]);
  if (route.length < 2)
    return (
      <div className="map-empty">
        ⌁<p>이 달리기는 지도 없이 기억해요.</p>
        <small>실내 달리기이거나 GPS 기록이 없어요.</small>
      </div>
    );
  return (
    <>
      <div
        ref={element}
        className="route-map"
        aria-label="러닝 경로 지도"
      />
      {tileError && (
        <p className="muted">
          배경 지도를 불러오지 못했어요. 달린 경로는 계속 표시됩니다.
        </p>
      )}
    </>
  );
}
