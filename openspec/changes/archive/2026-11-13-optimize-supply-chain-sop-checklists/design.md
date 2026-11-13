## Context

The existing workbooks inherit the customer lifecycle layout but their content was repeatedly replaced with generic text. The lifecycle map already defines five groups and 25 nodes, so the implementation must preserve the workbook presentation while using the map as the content dictionary.

## Goals / Non-Goals

**Goals:**

- Keep the source workbook layout and visual structure.
- Replace every Stage sheet body with stage-specific BAU, Quality, and Gate content.
- Align supporting tabs and labels with supply-chain terminology.
- Validate codes, order, and forbidden residual customer terms.

**Non-Goals:**

- No ERP code, API, database, or schema changes.
- No changes to the lifecycle map image in this change.

## Decisions

1. Use a single structured stage dictionary containing the 25 map nodes and their distinct BAU, Quality, and Gate lists.
2. Copy the five source workbooks, preserve styles and dimensions, then clear and rewrite textual cell content in place.
3. Rename Stage tabs to map codes and names while preserving their workbook position.
4. Keep common controls such as Owner, Next Action, date, and evidence, but require stage-specific content to make up the main checklist.
5. Validate output by reading every workbook and checking stage order plus forbidden residual customer terms.

## Risks / Trade-offs

- [Risk] Clearing cell values can remove useful template examples → Mitigation: rewrite the supporting tabs with supply-chain examples and retain the original formatting.
- [Risk] A stage dictionary drift can reintroduce image/Excel mismatch → Mitigation: keep the map node list in one generator source and validate every workbook against it.
- [Risk] Long Chinese text may overflow cells → Mitigation: preserve wrapped alignment and review representative sheets after generation.
