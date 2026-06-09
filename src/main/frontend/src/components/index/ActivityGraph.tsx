import { Heatmap } from "@ant-design/plots";
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
    const dayHeight = 15;
    const textOffset = 20;
    const res = getRes();

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
        if (count < 2) return token.colorSuccessBgHover;
        if (count < 4) return token.colorSuccessBorderHover;
        if (count < 8) return token.colorSuccess;
        return token.colorSuccessActive;
    };

    const chartData = useMemo(() => {
        return activityGrid.flatMap((week, weekIndex) =>
            week.map((day, dayIndex) => ({
                week: weekIndex,
                weekday: daysInWeek - 1 - dayIndex,
                date: day.date,
                count: day.count,
            }))
        );
    }, [activityGrid]);

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

        data.forEach((day, index) => {
            const date = new Date(day.date);
            const monthIndex = date.getMonth();
            if (monthIndex !== currentMonth) {
                currentMonth = monthIndex;
                labelMap.set(Math.floor(index / daysInWeek), monthNames[monthIndex]);
            }
        });

        return labelMap;
    }, [data, res.index.activityGraph.month]);

    const weekdayLabelByValue = new Map<number, string>([
        [6, res.index.activityGraph.weekday.mon],
        [4, res.index.activityGraph.weekday.wed],
        [2, res.index.activityGraph.weekday.fri],
    ]);
    const chartHeight = daysInWeek * dayHeight + textOffset;

    return (
        <div style={{ width: "100%" }}>
            <Heatmap
                data={chartData}
                xField="week"
                yField="weekday"
                colorField="count"
                height={chartHeight}
                autoFit
                legend={false}
                axis={{
                    x: {
                        position: "top",
                        labelFormatter: (value: string) => monthLabelByWeek.get(Number(value)) || "",
                        labelFill: token.colorTextSecondary,
                        tick: false,
                        line: false,
                    },
                    y: {
                        labelFormatter: (value: string) => weekdayLabelByValue.get(Number(value)) || "",
                        labelFill: token.colorTextSecondary,
                        tick: false,
                        line: false,
                    },
                }}
                scale={{
                    x: { padding: 0 },
                    y: { padding: 0 },
                }}
                style={{
                    fill: (datum: { count: number }) => getColor(datum.count),
                    stroke: token.colorBorderSecondary,
                    inset: 2,
                    radius: token.borderRadiusSM,
                }}
                tooltip={{
                    title: (datum: { date: string }) => datum.date || res.index.activityGraph.noData,
                    items: [
                        (datum: { count: number; date: string }) => ({
                            name: res.article.label,
                            value: datum.date ? datum.count : res.index.activityGraph.noData,
                        }),
                    ],
                }}
            />
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
