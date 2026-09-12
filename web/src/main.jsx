import React, { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import { loadRuns } from "./RunRepository.js";
import { statistics, bestSplit, improvement } from "./domain/statistics.js";
import { achievements } from "./domain/achievements.js";
import { distance, duration, pace, dateLabel } from "./domain/formatters.js";
import RouteMap from "./components/RouteMap.jsx";
import "./styles.css";

function PerformanceGraphic({ stats }) {
  return (
    <div className="performance-graphic" aria-hidden="true">
      <div className="pace-ring">
        <span>{duration(stats.best1k)}</span>
        <small>BEST 1K</small>
      </div>
      <div className="performance-line" />
      <div className="performance-meta">
        <span>{stats.count} RUNS</span>
        <span>{distance(stats.totalDistance)} KM</span>
      </div>
    </div>
  );
}
function RunCard({ run, index }) {
  return (
    <a className="run-card" href={`#/runs/${run.id}`}>
      <div className="run-number">
        {String(index).padStart(2, "0")}
        <span>RUN</span>
      </div>
      <div className="run-card-body">
        <div className="eyebrow">{dateLabel(run.date)}</div>
        <h3>{distance(run.distanceMeters)} km Run</h3>
        <div className="run-metrics">
          <strong>
            {distance(run.distanceMeters)} <small>km</small>
          </strong>
          <span>
            {duration(run.durationSeconds)} <small>시간</small>
          </span>
          <span>
            {duration(pace(run))} <small>/km</small>
          </span>
        </div>
      </div>
      <span className="arrow">↗</span>
    </a>
  );
}
function Dashboard({ runs }) {
  const stats = statistics(runs),
    badges = achievements(runs),
    better = improvement(runs);
  const next = [10000, 25000, 50000, 100000].find(
    (n) => n > stats.totalDistance,
  );
  return (
    <>
      <section className="hero">
        <div className="hero-copy">
          <div className="eyebrow">RUN LOG</div>
          <h1>
            달린 만큼,
            <br />기록은 쌓인다.
          </h1>
          <p>
            거리, 페이스, 구간 기록을
            <br />한눈에 확인해요.
          </p>
          <a className="button" href="#/runs">
            전체 기록 보기 <span>↗</span>
          </a>
        </div>
        <PerformanceGraphic stats={stats} />
        <span className="hero-note">DISTANCE · PACE · PROGRESS</span>
      </section>
      <section className="stats" aria-label="누적 기록">
        {[
          ["누적 거리", distance(stats.totalDistance), "km", "◎"],
          ["러닝 횟수", stats.count, "회", "⚑"],
          ["최장 거리", distance(stats.longest), "km", "↗"],
          ["최고 1 km", duration(stats.best1k), "/km", "ϟ"],
        ].map(([label, value, unit, icon]) => (
          <div className="stat" key={label}>
            <div className="stat-label">
              {label}
              <span>{icon}</span>
            </div>
            <strong>
              {value}
              <small>{unit}</small>
            </strong>
          </div>
        ))}
      </section>
      <div className="dashboard-columns">
        <section>
          <div className="section-heading">
            <div>
              <div className="eyebrow">RECENT RUNS</div>
              <h2>최근 기록</h2>
            </div>
            <a href="#/runs">모두 보기 ↗</a>
          </div>
          {runs.length ? (
            runs
              .slice(0, 3)
              .map((run, i) => (
                <RunCard key={run.id} run={run} index={runs.length - i} />
              ))
          ) : (
            <div className="empty">
              아직 러닝 기록이 없습니다.
            </div>
          )}
        </section>
        <aside>
          <section className="milestone">
            <span className="tag">
              {better ? "PERSONAL BEST" : "NEXT GOAL"}
            </span>
            <div className="milestone-icon">{better ? "✧" : "⚑"}</div>
            <h2>
              {better
                ? "최고 1 km 기록 갱신"
                : next
                  ? `다음 목적지는 ${next / 1000} km`
                  : "100 km 달성"}
            </h2>
            <p>
              {better
                ? `최근 달리기에서 최고 1 km를 ${duration(better.seconds)} 단축했어요.`
                : "다음 누적 거리 목표까지의 진행 상황입니다."}
            </p>
            {better && (
              <strong className="record">
                {duration(better.best)} <small>/km</small>
              </strong>
            )}
            {next && (
              <>
                <div className="progress-label">
                  <span>누적 {distance(stats.totalDistance)} km</span>
                  <span>{next / 1000} km</span>
                </div>
                <progress value={stats.totalDistance} max={next} />
                <small>
                  앞으로 {distance(next - stats.totalDistance)} km
                </small>
              </>
            )}
          </section>
          <section className="badge-preview">
            <div className="section-heading">
              <h2>기록 배지</h2>
              <a href="#/achievements">모두 보기 ↗</a>
            </div>
            <div className="mini-badges">
              {badges
                .filter((b) => b.unlocked)
                .slice(0, 3)
                .map((b) => (
                  <div key={b.id}>
                    <span>{b.icon}</span>
                    <small>{b.title}</small>
                  </div>
                ))}
            </div>
            {!stats.count && <p>첫 기록을 추가하면 배지가 열립니다.</p>}
          </section>
        </aside>
      </div>
    </>
  );
}
function Detail({ run }) {
  if (!run)
    return (
      <div className="empty">
        <h1>기록을 찾을 수 없어요.</h1>
        <a href="#/runs">목록으로 돌아가기</a>
      </div>
    );
  const best = bestSplit(run);
  return (
    <>
      <a className="back" href="#/runs">
        ← 전체 기록
      </a>
      <header className="page-heading">
        <div className="eyebrow">
          {dateLabel(run.date)} · {run.startTime.slice(11, 16)} (기록 현지 시각)
        </div>
        <h1>{distance(run.distanceMeters)} km Run</h1>
      </header>
      <section className="stats detail-stats">
        {[
          [distance(run.distanceMeters), "km", "달린 거리"],
          [duration(run.durationSeconds), "", "러닝 시간"],
          [duration(pace(run)), "/km", "평균 페이스"],
        ].map(([v, u, l]) => (
          <div className="stat" key={l}>
            <div className="stat-label">{l}</div>
            <strong>
              {v}
              <small>{u}</small>
            </strong>
          </div>
        ))}
      </section>
      <div className="detail-columns">
        <section className="panel">
          <div className="section-heading">
            <h2>경로</h2>
            <span className="muted">출발 ● · 도착 ●</span>
          </div>
          <RouteMap route={run.route} />
        </section>
        <section className="panel">
          <h2>1 km 구간</h2>
          <p className="muted">각 구간을 달리는 데 걸린 시간</p>
          {run.splits?.length ? (
            <div className="splits">
              {run.splits.map((s, i) => (
                <div className="split" key={i}>
                  <span>
                    {s.distanceMeters === 1000
                      ? `${i + 1} km`
                      : `마지막 ${distance(s.distanceMeters)} km`}
                  </span>
                  <div className="split-track">
                    <div
                      style={{
                        width: `${Math.min(100, best ? (best / ((s.durationSeconds / s.distanceMeters) * 1000)) * 100 : 100)}%`,
                      }}
                    />
                  </div>
                  <strong>
                    {duration(s.durationSeconds)}{" "}
                    {s.distanceMeters === 1000 && s.durationSeconds === best
                      ? "★"
                      : ""}
                  </strong>
                </div>
              ))}
            </div>
          ) : (
            <p className="empty">구간 기록이 없는 달리기예요.</p>
          )}
          <p className="muted">★ 가장 빠른 온전한 1 km 구간</p>
          {run.heartRate && (
            <p>
              심박수 · 평균 {run.heartRate.average} / 최대 {run.heartRate.max}{" "}
              bpm
            </p>
          )}
          {run.cadence && <p>평균 케이던스 · {run.cadence.average} spm</p>}
        </section>
      </div>
    </>
  );
}
function App() {
  const [runs, setRuns] = useState([]),
    [status, setStatus] = useState("loading"),
    [hash, setHash] = useState(location.hash),
    [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const handler = () => {
      setHash(location.hash);
      window.scrollTo(0, 0);
    };
    window.addEventListener("hashchange", handler);
    return () => window.removeEventListener("hashchange", handler);
  }, []);
  useEffect(() => {
    const controller = new AbortController();
    setStatus("loading");
    loadRuns(controller.signal)
      .then((r) => {
        setRuns(r);
        setStatus("ready");
      })
      .catch((e) => {
        if (e.name !== "AbortError") setStatus("error");
      });
    return () => controller.abort();
  }, [attempt]);
  const page = hash.replace(/^#\/?/, "") || "home";
  return (
    <>
      <header className="site-header">
        <a href="#/" className="brand">
          <span className="brand-symbol">R</span>
          <span>
            RUN LOG<small>DISTANCE · PACE · PROGRESS</small>
          </span>
        </a>
        <nav aria-label="주 메뉴">
          {[
            ["home", "대시보드", "#/"],
            ["runs", "달리기 기록", "#/runs"],
            ["achievements", "기록 배지", "#/achievements"],
          ].map(([id, label, url]) => (
            <a
              key={id}
              href={url}
              aria-current={
                page === id || page.startsWith(`${id}/`) ? "page" : undefined
              }
            >
              {label}
            </a>
          ))}
        </nav>
        <span className="club-mark">
          SINCE 2026 <span>↗</span>
        </span>
      </header>
      <main>
        {status === "loading" ? (
          <div className="empty" role="status">
            러닝 기록을 불러오는 중…
          </div>
        ) : status === "error" ? (
          <div className="empty" role="alert">
            <p>기록을 불러오지 못했어요.</p>
            <button className="button" onClick={() => setAttempt((a) => a + 1)}>
              다시 시도
            </button>
          </div>
        ) : page === "home" ? (
          <Dashboard runs={runs} />
        ) : page === "runs" ? (
          <>
            <header className="page-heading">
              <div className="eyebrow">RUN HISTORY</div>
              <h1>전체 기록</h1>
              <p>총 {runs.length}회의 러닝.</p>
            </header>
            <div className="runs-grid">
              {runs.map((r, i) => (
                <RunCard key={r.id} run={r} index={runs.length - i} />
              ))}
            </div>
            {!runs.length && (
              <div className="empty">
                아직 러닝 기록이 없습니다.
              </div>
            )}
          </>
        ) : page.startsWith("runs/") ? (
          <Detail run={runs.find((r) => r.id === page.slice(5))} />
        ) : page === "achievements" ? (
          <>
            <header className="page-heading">
              <div className="eyebrow">ACHIEVEMENTS</div>
              <h1>기록 배지</h1>
              <p>
                달성한 배지 {achievements(runs).filter((b) => b.unlocked).length}개.
              </p>
            </header>
            <div className="badges-grid">
              {achievements(runs).map((b) => (
                <article
                  className={`badge ${b.unlocked ? "" : "locked"}`}
                  key={b.id}
                >
                  <span className="badge-icon">{b.icon}</span>
                  <div className="eyebrow">
                    {b.unlocked ? "UNLOCKED" : "LOCKED"}
                  </div>
                  <h2>{b.title}</h2>
                  <p>{b.description}</p>
                </article>
              ))}
            </div>
          </>
        ) : (
          <div className="empty">
            <h1>페이지를 찾을 수 없습니다.</h1>
            <a href="#/">대시보드로 돌아가기</a>
          </div>
        )}
      </main>
      <footer>
        <span>KEEP RUNNING.</span>
        <span>DISTANCE · PACE · PROGRESS ↗</span>
      </footer>
    </>
  );
}
createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
