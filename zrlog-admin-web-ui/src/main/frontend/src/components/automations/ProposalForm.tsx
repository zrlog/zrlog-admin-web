import { Alert, Button, Card, Drawer, Space, Typography } from "antd";
import Input from "@zrlog/ui/antd/Input";
import { useEffect, useRef, useState } from "react";
import { getRes } from "../../utils/constants";
import type { Definition, Plan, Request } from "./types";

type Proposal = { explanation: string; plans: Plan[] };

export default function ProposalForm({
    request,
    definitions,
    onClose,
    onChoose,
}: {
    request: Request;
    definitions: Definition[];
    onClose: () => void;
    onChoose: (plan: Plan) => void;
}) {
    const res = getRes().assistant;
    const [input, setInput] = useState("");
    const [pending, setPending] = useState(false);
    const [proposal, setProposal] = useState<Proposal>();
    const [error, setError] = useState<string>();
    const active = useRef(true);
    useEffect(() => {
        active.current = true;
        return () => {
            active.current = false;
        };
    }, []);
    const propose = async () => {
        if (pending || !input.trim()) return;
        setPending(true);
        setProposal(undefined);
        setError(undefined);
        try {
            const result = await request<Proposal>("propose", {
                input,
                timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
            });
            if (active.current) setProposal(result);
        } catch (failure) {
            if (active.current) setError(failure instanceof Error ? failure.message : res.failedRequest);
        } finally {
            if (active.current) setPending(false);
        }
    };
    return (
        <Drawer title={res.askAssistant} open onClose={onClose} size={520} closable={{ placement: "end" }}>
            <Space orientation="vertical" size="large" style={{ width: "100%" }}>
                <Typography.Paragraph>{res.proposalHelp}</Typography.Paragraph>
                <label htmlFor="automation-goal">{res.goal}</label>
                <Input.TextArea
                    id="automation-goal"
                    value={input}
                    onChange={(event) => setInput(event.target.value)}
                    maxLength={4000}
                    showCount
                    rows={4}
                    disabled={pending}
                    placeholder={res.proposalExample}
                />
                <Button type="primary" onClick={() => void propose()} loading={pending} disabled={!input.trim()}>
                    {res.suggestPlans}
                </Button>
                {error && <Alert type="error" title={error} showIcon role="alert" />}
                {proposal && (
                    <>
                        <Typography.Paragraph style={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>
                            {proposal.explanation}
                        </Typography.Paragraph>
                        {proposal.plans.length > 0 && <Alert type="info" title={res.proposalReview} />}
                        {proposal.plans.map((plan, index) => (
                            <Card key={index} title={plan.name}>
                                <Typography.Paragraph>
                                    {definitions.find((item) => item.type === plan.type)?.title}
                                </Typography.Paragraph>
                                <Button onClick={() => onChoose(plan)}>{res.reviewPlan}</Button>
                            </Card>
                        ))}
                    </>
                )}
            </Space>
        </Drawer>
    );
}
