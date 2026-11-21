import {useEffect, useMemo, useState} from 'react';
import type {Key} from 'react-aria-components';
import {ListChecks} from 'lucide-react';
import {Tree, type UiTreeNode} from '../../ui/Tree';
import {buildTree, folderIds, type FeatureSummary, type TreeNode} from './buildTree';
import {treeStrings as t} from './strings';

/** FE-QA-3 / F12: the feature's name is the primary text (the file name when the name is empty). */
export function fileRowText(name: string, fileName: string): {label: string; secondary: string | undefined} {
    const trimmed = name.trim();
    return trimmed ? {label: trimmed, secondary: fileName} : {label: fileName, secondary: undefined};
}

function fileNode(feature: FeatureSummary, fileName: string, path: string): UiTreeNode {
    const {label, secondary} = fileRowText(feature.name, fileName);
    const [firstTag, ...moreTags] = feature.tags;
    return {
        id: feature.id,
        kind: 'file',
        label,
        secondary,
        title: path,
        meta: (
            <>
                {firstTag ? (
                    <span className="inline-flex min-w-0 items-center gap-1" title={feature.tags.join(' ')}>
                        <span className="max-w-24 truncate rounded-sm bg-surface-hover px-1.5 py-0.5 font-mono text-xs text-text-muted">{firstTag}</span>
                        {moreTags.length > 0 ? (
                            <span className="text-xs text-text-muted">
                                <span aria-hidden="true">+{moreTags.length}</span>
                                <span className="sr-only">{t.moreTags(moreTags.join(' '))}</span>
                            </span>
                        ) : null}
                    </span>
                ) : null}
                <span className="inline-flex w-8 shrink-0 items-center justify-end gap-1 text-xs tabular-nums text-text-muted">
                    <ListChecks aria-hidden="true" size={12}/>
                    <span className="sr-only">{t.testCases(feature.testCaseCount)}</span>
                    <span aria-hidden="true">{feature.testCaseCount}</span>
                </span>
            </>
        ),
    };
}

function toUiNodes(nodes: TreeNode[]): UiTreeNode[] {
    return nodes.map((node) =>
        node.kind === 'folder'
            ? {id: node.id, kind: 'folder', label: node.name, children: toUiNodes(node.children)}
            : fileNode(node.feature, node.name, node.path),
    );
}

/** D10 / AC-5.1: the virtualized tree, folders expanded by default. */
export function FeatureTree({features, selectedId, onOpen}: { features: readonly FeatureSummary[]; selectedId: string | null; onOpen: (id: string) => void }) {
    const tree = useMemo(() => buildTree(features), [features]);
    const nodes = useMemo(() => toUiNodes(tree), [tree]);
    const folders = useMemo(() => folderIds(tree), [tree]);
    const [collapsed, setCollapsed] = useState<Set<Key>>(new Set());
    useEffect(() => setCollapsed(new Set()), [features]);
    const expanded = useMemo(() => folders.filter((id) => !collapsed.has(id)), [folders, collapsed]);
    return (
        <Tree
            label={t.treeLabel}
            nodes={nodes}
            folderIds={folders}
            expandedKeys={expanded}
            onExpandedChange={(keys) => setCollapsed(new Set(folders.filter((id) => !keys.has(id))))}
            selectedKey={selectedId}
            onSelect={onOpen}
        />
    );
}
