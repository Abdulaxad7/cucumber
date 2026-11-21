import {CircleCheck, CircleDashed, CirclePlay, CircleSlash, type LucideIcon} from 'lucide-react';
import type {components} from '../../api/generated/report-service';
import {Badge, type BadgeTone} from '../../ui/Badge';
import {runStrings as t} from './strings';

type RunStatus = components['schemas']['TestRunStatus'];

const look: Record<RunStatus, {icon: LucideIcon; tone: BadgeTone}> = {
    PLANNED: {icon: CircleDashed, tone: 'neutral'},
    IN_PROGRESS: {icon: CirclePlay, tone: 'primary'},
    COMPLETED: {icon: CircleCheck, tone: 'neutral'},
    CANCELLED: {icon: CircleSlash, tone: 'neutral'},
};

/** DS-2: run statuses use the neutral Badge with an icon and the word. */
export function RunStatusBadge({status}: { status: RunStatus }) {
    const {icon: Icon, tone} = look[status];
    return (
        <Badge tone={tone} icon={<Icon aria-hidden="true" size={12}/>}>
            {t.runStatus[status] ?? status}
        </Badge>
    );
}
