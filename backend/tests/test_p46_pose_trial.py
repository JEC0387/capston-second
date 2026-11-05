"""No-network profile isolation and evidence binding, not a visual quality test."""
import hashlib
import inspect

import p45_public_demo_trial as baseline
from p46_pose_trial import POSE_PROMPT


def test_pose_profile_is_exact_executed_prompt_without_mutating_baseline():
    # The actual P46 execution's experiment-plan.json records this independent digest.
    assert hashlib.sha256(POSE_PROMPT.encode()).hexdigest() == "14cba6e2232cd82a3ffa35913d7e0defa05eed2e3b1e39ac5afa28c6fe2664af"
    assert POSE_PROMPT != baseline.PROMPT
    assert inspect.signature(baseline.main).parameters["prompt"].default == baseline.PROMPT
