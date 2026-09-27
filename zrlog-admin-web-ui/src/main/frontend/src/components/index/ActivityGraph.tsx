import { theme } from "antd";
import React, { useMemo } from "react";
import { getRes } from "../../utils/constants";

export interface ActivityDay {
    date: string;
    count: number;
}

interface ActivityGraphProps {
    data: ActivityDay[];
}

const ActivityGraph: React.FC<ActivityGraphProps> = ({ data }) => {
    const { token } = theme.useToken();
    const daysInWeek = 7;
    const res = getRes();
    const cellSize = 10;
    const cellGap = 2;
    const weekdayColumnWidth = 28;

    const activityGrid = useMemo(() => {
        const grid: ActivityDay[][] = [];
        let currentWeek: ActivityDay[] = [];

        const firstDate = new Date(data[0]?.date);
        const firstDayOfWeek = firstDate.getDay();
        const offsetStart = (firstDayOfWeek + 6) % 7;

        for (let i = 0; i < offsetStart; i++) {
            currentWeek.push({ date: "", count: 0 });
        }

        data.forEach((day) => {
            currentWeek.push(day);

            if (currentWeek.length === daysInWeek) {
                grid.push(currentWeek);
                currentWeek = [];
            }
        });

        while (currentWeek.length > 0 && currentWeek.length < daysInWeek) {
            currentWeek.push({ date: "", count: 0 });
        }
        if (currentWeek.length === daysInWeek) {
            grid.push(currentWeek);
        }

        return grid;
    }, [data]);

    const getColor = (count: number) => {
        if (count === 0) return token.colorFillSecondary;
        if (count < 2) return token.colorSuccessBg;
        if (count < 4) return token.colorSuccessBorder;
        if (count < 8) return token.colorSuccess;
        return token.colorSuccessActive;
    };

    const monthLabelByWeek = useMemo(() => {
        const month = res.index.activityGraph.month;
        const monthNames = [
            month.jan,
            month.feb,
            month.mar,
            month.apr,
            month.may,
            month.jun,
            month.jul,
            month.aug,
            month.sep,
            month.oct,
            month.nov,
            month.dec,
        ];
        const labelMap = new Map<number, string>();
        let currentMonth = -1;

        activityGrid.forEach((week, weekIndex) => {
            week.forEach((day) => {
                if (!day.date) {
                    return;
                }
                const date = new Date(day.date);
                const monthIndex = date.getMonth();
                if (monthIndex !== currentMonth) {
                    currentMonth = monthIndex;
                    labelMap.set(weekIndex, monthNames[monthIndex]);
                }
            });
        });

        return labelMap;
    }, [activityGrid, res.index.activityGraph.month]);

    const weekdayLabels = new Map<number, string>([
        [0, res.index.activityGraph.weekday.mon],
        [2, res.index.activityGraph.weekday.wed],
        [4, res.index.activityGraph.weekday.fri],
    ]);
    const weekCount = Math.max(activityGrid.length, 1);
    const graphWidth = weekCount * cellSize + Math.max(weekCount - 1, 0) * cellGap;
    const graphHeight = daysInWeek * cellSize + (daysInWeek - 1) * cellGap;
    const monthColumnStyle = {
        display: "grid",
        gridTemplateColumns: `repeat(${weekCount}, ${cellSize}px)`,
        gap: cellGap,
        width: graphWidth,
        color: token.colorTextSecondary,
        fontSize: 11,
        lineHeight: "16px",
        marginLeft: weekdayColumnWidth,
    };
    const gridStyle = {
        display: "grid",
        gridTemplateColumns: `repeat(${weekCount}, ${cellSize}px)`,
        gridTemplateRows: `repeat(${daysInWeek}, ${cellSize}px)`,
        gap: cellGap,
        width: graphWidth,
        height: graphHeight,
    };
    const weekdayStyle = {
        display: "grid",
        gridTemplateRows: `repeat(${daysInWeek}, ${cellSize}px)`,
        gap: cellGap,
        width: weekdayColumnWidth,
        color: token.colorTextSecondary,
        fontSize: 11,
        lineHeight: `${cellSize}px`,
        flexShrink: 0,
    };

    return (
        <div
            aria-label={res.index.activity}
            className="activity-graph"
            role="img"
            style={{ width: "100%", overflowX: "auto", paddingBottom: 2 }}
        >
            <div style={{ width: graphWidth + weekdayColumnWidth }}>
                <div style={monthColumnStyle}>
                    {Array.from({ length: weekCount }).map((_, weekIndex) => (
                        <span key={weekIndex} style={{ whiteSpace: "nowrap" }}>
                            {monthLabelByWeek.get(weekIndex) || ""}
                        </span>
                    ))}
                </div>
                <div style={{ display: "flex", gap: 0, marginTop: 4 }}>
                    <div style={weekdayStyle}>
                        {Array.from({ length: daysInWeek }).map((_, dayIndex) => (
                            <span key={dayIndex}>{weekdayLabels.get(dayIndex) || ""}</span>
                        ))}
                    </div>
                    <div style={gridStyle}>
                        {activityGrid.flatMap((week, weekIndex) =>
                            week.map((day, dayIndex) => {
                                const cellTitle = day.date
                                    ? `${day.date}: ${day.count} ${res.article.label}`
                                    : res.index.activityGraph.noData;
                                return (
                                    <span
                                        aria-label={cellTitle}
                                        key={`${weekIndex}-${day.date || dayIndex}`}
                                        title={cellTitle}
                                        style={{
                                            width: cellSize,
                                            height: cellSize,
                                            borderRadius: token.borderRadiusXS,
                                            background: day.date ? getColor(day.count) : "transparent",
                                            border: `${token.lineWidth}px ${token.lineType} ${
                                                day.date ? token.colorBorderSecondary : "transparent"
                                            }`,
                                            boxSizing: "border-box",
                                            display: "block",
                                        }}
                                    />
                                );
                            })
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
};

export const generateCompleteData = (partialData: ActivityDay[]): ActivityDay[] => {
    const today = new Date();
    const startDate = new Date(today);
    startDate.setFullYear(today.getFullYear() - 1);
    startDate.setDate(1);

    const completeData: ActivityDay[] = [];
    const partialDataMap = new Map(partialData.map((item) => [item.date, item.count]));

    const currentDate = startDate;
    while (currentDate <= today) {
        const dateStr = currentDate.toISOString().split("T")[0];
        completeData.push({
            date: dateStr,
            count: partialDataMap.get(dateStr) || 0, // 填充缺失数据为 0
        });
        currentDate.setDate(currentDate.getDate() + 1);
    }

    return completeData;
};

export default ActivityGraph;
