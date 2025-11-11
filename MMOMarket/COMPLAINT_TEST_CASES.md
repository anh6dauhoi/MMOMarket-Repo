Workflow	Complaint Workflow
Test requirement	End-to-end verification of complaint lifecycle: list/detail views, cancel (NEW), escalate (IN_PROGRESS/PENDING_CONFIRMATION), confirm resolution (PENDING_CONFIRMATION), and auto-resolve after 3 days timeout; including notifications/emails/assignments and validation errors.
Number of TCs	6
Testing Round	Passed	Failed	Pending	N/A
Round 1	0	0	6	0
Round 2	0	0	6	0
Round 3	0	0	6	0

Test Case ID	Test Case Description	Test Case Procedure	Expected Results	Pre-conditions	Round 1	Test date	Tester	Round 2	Test date	Tester	Round 3	Test date	Tester	Note
Scenario D												
ID1	View complaint list and detail	1) Login as Customer (owner of complaints).
2) Navigate to "/account/complaints".
3) (Optional) Apply filters: status=NEW/IN_PROGRESS/PENDING_CONFIRMATION/ESCALATED/RESOLVED.
4) (Optional) Use search box with keywords matching status/type/description.
5) Click any complaint row to open detail at "/account/complaints/{id}".	- List page displays complaints sorted by createdAt DESC by default (unless sorting provided).
- Filtering by status works; search narrows results by complaintType/description/status text.
- Detail view shows: id, status (formatted + raw), description, evidence list (if any), product name (or N/A), transaction id (if any), seller name, admin handler (if any).
- Available actions reflect status: canCancel only when NEW; canRequestAdmin when IN_PROGRESS or PENDING_CONFIRMATION; chat always allowed.	- Customer test account exists and is authenticated.
- At least one complaint exists for this customer (preferably across different statuses for filter testing).	Pending			Pending			Pending			Covers GET /account/complaints and GET /account/complaints/{id}
ID2	Cancel complaint in NEW status	1) Login as complaint owner (Customer).
2) Identify a complaint in NEW status (from list page) with its {id}.
3) Send POST "/account/complaints/{id}/cancel" (ensure CSRF/session as applicable).
4) Refresh detail or list to verify status.	- HTTP 200 with body { success: true, message: "Complaint cancelled successfully" }.
- Complaint status changes from NEW -> CANCELLED; updatedAt updated.
- Seller receives a notification: title "Complaint Cancelled by Customer".
- Customer can no longer perform cancel on this complaint.	- Complaint exists in NEW status and belongs to the logged-in customer.	Pending			Pending			Pending			Negative: If status != NEW, expect 400 with error message
ID3	Escalate complaint from IN_PROGRESS (customer)	1) Login as complaint owner (Customer).
2) Identify a complaint in IN_PROGRESS.
3) POST "/account/complaints/{id}/escalate" with JSON { "reason": "<at least 20 characters detailed reason>" }.
4) Check complaint detail and notifications.	- HTTP 200 with { success: true, message includes "escalated" }.
- Complaint status changes to ESCALATED.
- Admin handler is assigned (non-null admin_handler_id).
- Email is sent to assigned admin (verify via logs or inbox if configured).
- Notifications are created for: assigned Admin, Customer, and Seller indicating escalation.	- Complaint exists in IN_PROGRESS and belongs to the logged-in customer.
- System has at least one ADMIN user.	Pending			Pending			Pending			Negative: reason missing or <20 chars → 400 with error; status not in allowed set → 400
Scenario E												
ID4	Escalate complaint from PENDING_CONFIRMATION (customer)	1) Login as complaint owner (Customer).
2) Identify a complaint in PENDING_CONFIRMATION.
3) POST "/account/complaints/{id}/escalate" with JSON { "reason": "<>=20 chars>" }.
4) Inspect result.	- Same expected results as ID3 (status -> ESCALATED, admin assigned, emails/notifications created).	- Complaint exists in PENDING_CONFIRMATION and belongs to customer.
- At least one ADMIN exists.	Pending			Pending			Pending			Use to cover the second allowed source status
ID5	Confirm resolution (accept) from PENDING_CONFIRMATION	1) Login as complaint owner (Customer).
2) Identify complaint in PENDING_CONFIRMATION.
3) POST "/account/complaints/{id}/confirm" with JSON { "accept": true }.
4) Verify change on detail page.	- HTTP 200 with { success: true, message includes "Solution accepted" }.
- Complaint status changes to RESOLVED; updatedAt updated.
- Seller receives notification: title "Complaint Resolved".	- Complaint exists in PENDING_CONFIRMATION and belongs to the customer.	Pending			Pending			Pending			Negative: For { "accept": false } API returns 400 asking to use escalate flow
ID6	Auto-resolve PENDING_CONFIRMATION after 3 days timeout	1) Prepare a complaint in PENDING_CONFIRMATION with updatedAt set to >= 3 days ago (DB seed or clock manipulation).
2) Wait for the scheduled job (per current cron, runs every minute in test) to execute.
3) Re-open complaint detail or query DB after scheduler runs.	- Complaint status automatically changes to RESOLVED.
- Notifications are created for both Customer and Seller indicating auto-resolve.
- No user interaction required.	- Scheduler enabled.
- A complaint exists in PENDING_CONFIRMATION with updatedAt sufficiently old.	Pending			Pending			Pending			Edge: Add a reminder test before timeout (optional)


