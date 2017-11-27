package solver;

/**
 * COMP3702 A3 2017
 * v1.0
 * Support code last updated by Nicholas Collins 19/10/17
 * MDP agent last updated by Arda Akgur 17/11/17
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import problem.VentureManager;
import problem.Matrix;
import problem.ProblemSpec;

/**
 * Solves finance based MDP problem
 */
public class MySolver implements FundingAllocationAgent {

	private ProblemSpec spec = new ProblemSpec();
	private VentureManager ventureManager;
	private List<Matrix> probabilities;

	private List<Double> salePrices;
	private Map<Status, Expense> seMap = new HashMap<>();
	private List<List<Integer>> actions = new ArrayList<>();
	private List<List<Integer>> statuses = new ArrayList<>();;

	/**
	 * Constructs instance
	 */
	public MySolver(ProblemSpec spec) throws IOException {
		this.spec = spec;
		ventureManager = spec.getVentureManager();
		probabilities = spec.getProbabilities();
		salePrices = spec.getSalePrices();
	}

	/**
	 * Does offline MDP calculation
	 */
	public void doOfflineComputation() {

		getActions(actions, ventureManager.getMaxAdditionalFunding());
		getActions(statuses, ventureManager.getMaxManufacturingFunds());
		valueIteration();

	}

	/**
	 * Returns a list of Map.Entry depending on the fortnight Creates Map.Entry from
	 * the this.seMap
	 * 
	 * @require i as positive integer includes 0
	 * @ensure
	 */
	private List<Map.Entry<Status, Expense>> populateEntries(int i) {

		List<Map.Entry<Status, Expense>> output = new ArrayList<>();
		Set<Map.Entry<Status, Expense>> entryset = seMap.entrySet();
		Iterator<Map.Entry<Status, Expense>> iterator = entryset.iterator();
		while (iterator.hasNext()) {

			Map.Entry<Status, Expense> next = iterator.next();
			if (next.getKey().time == i)
				output.add(next);
		}
		return output;
	}

	/**
	 * Calculates if the expense is possible depending on the current funds and
	 * predicted funds
	 */
	private boolean isPossibleExpense(List<Integer> funds, List<Integer> ventures) {
		for (int i = 0; i < funds.size(); i++) {
			if (ventures.get(i) > funds.get(i)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * MDP ValueIteration algorithm based on Russel & Norvig Artificial
	 * Intelligence: A modern Approach 3rd edition Chapter 17.2
	 */
	private void valueIteration() {
		for (int i = 0; i <= spec.getNumFortnights(); i++) {
			List<Map.Entry<Status, Expense>> entrymap = null;
			if (i > 0)
				entrymap = populateEntries(i);
			for (List<Integer> status : statuses) {
				double max = 0;
				List<Integer> sequence = null;
				for (List<Integer> action : actions) {
					if (funding(action, status) <= ventureManager.getMaxManufacturingFunds()) {
						double expense;
						if (i == 0)
							expense = handleFinal(status, action);
						else
							expense = handleRegular(entrymap, status, action);

						if (expense > max) {
							max = expense;
							sequence = action;
						}
					}
				}
				seMap.put(new Status(status, i), new Expense(sequence, max));
			}
		}
	}

	/**
	 * Returns expense decision depending on the Status It considers the future
	 * Status
	 */
	private double handleRegular(List<Map.Entry<Status, Expense>> entrymap, List<Integer> status,
			List<Integer> action) {
		double r = handleFinal(status, action);
		List<Integer> funds = new ArrayList<>(ventureManager.getNumVentures());
		for (int i = 0; i < ventureManager.getNumVentures(); i++) {
			funds.add(status.get(i) + action.get(i));
		}
		Map<Status, Expense> prediction = new HashMap<>();
		for (Map.Entry<Status, Expense> entry : entrymap) {
			List<Integer> ventures = entry.getKey().ventures;
			if (isPossibleExpense(funds, ventures)) {
				prediction.put(entry.getKey(), entry.getValue());
			}
		}
		double d = 0;
		int c = 0;
		for (Status key : prediction.keySet()) {
			double probability = 0;
			Matrix matrix = probabilities.get(c);
			List<Integer> ventures = key.ventures;
			for (int i = 0; i < ventures.size(); i++) {
				probability *= matrix.get(funds.get(i), ventures.get(i));
			}
			d += probability * prediction.get(key).expense;
			c++;
		}
		r += d;
		return r;

	}

	/**
	 * Returns expense for the final fortNight Does not considers the upcoming
	 * forthnights
	 */
	private double handleFinal(List<Integer> status, List<Integer> action) {
		double r = 0;
		for (int i = 0; i < ventureManager.getNumVentures(); i++) {

			int treshold = status.get(i) + action.get(i);
			double v = 0;
			if (treshold == 0) {

				v = -0.25 * salePrices.get(i);
			} else {

				Matrix matrix = probabilities.get(i);
				int size = matrix.getRow(treshold).size();
				double res = 0;

				for (int j = 0; j < size; j++) {
					res += matrix.get(treshold, j) * j;
				}
				v = 0.6 * res * salePrices.get(i);
			}
			r += v;
		}
		return r;
	}

	/**
	 * returns a list of integer where the values are the funding for the current
	 * action depending on the status
	 */
	private int funding(List<Integer> action, List<Integer> status) {
		int total = 0;

		for (int i = 0; i < action.size(); i++) {
			total += action.get(i);
			total += status.get(i);
		}
		return total;
	}

	/**
	 * Defines the (S)tate or (A)action sets of MDP problem Set of all states S or
	 * set of all actions A depending on the treshold value
	 */
	private void getActions(List<List<Integer>> output, int treshold) {
		boolean check = false;
		if (ventureManager.getNumVentures() == 3)
			check = true;

		int len = treshold + 1;

		for (int i = 0; i < len; i++) {
			for (int j = 0; j < len; j++) {

				if (!check) {
					if ((i + j) < len) {
						List<Integer> k = new ArrayList<>(ventureManager.getNumVentures());
						k.add(i);
						k.add(j);
						output.add(k);
					}
				} else {
					for (int l = 0; l < len; l++) {
						if ((i + j + l) < len) {
							List<Integer> k = new ArrayList<>(ventureManager.getNumVentures());
							k.add(i);
							k.add(j);
							k.add(l);
							output.add(k);
						}
					}
				}
			}
		}
	}

	/**
	 * Generates additiongal funding amounts Accepts manufacturingFunds as current
	 * Status Returns corresponding expense sequence(A) from the seMap
	 */
	public List<Integer> generateAdditionalFundingAmounts(List<Integer> manufacturingFunds, int numFortnightsLeft) {
		Status status = new Status(manufacturingFunds, numFortnightsLeft);
		return seMap.get(status).sequence;
	}

	/**
	 * A struct to define Status Each Status has time which is basically
	 * numFortnightsLeft && ventures which is basically State (S)
	 */
	private class Status {
		public List<Integer> ventures;
		public int time;

		public Status(List<Integer> ventures, int time) {
			this.time = time;
			this.ventures = ventures;
		}

		@Override
		public boolean equals(Object other) {
			if (other instanceof Status) {
				Status status = (Status) other;

				if (this.time == status.time) {
					if (this.ventures.size() == status.ventures.size()) {
						for (Integer i : this.ventures) {
							if (!status.ventures.contains(i)) {
								return false;
							}
						}
						return true;
					}
				}
			}
			return false;
		}

		@Override
		public String toString() {
			return "" + ventures;
		}

		@Override
		public int hashCode() {
			return 31 * Integer.hashCode(time) + 13 * ventures.hashCode();
		}
	}

	/**
	 * Expense struct represents an expense Each Expense has an expense that
	 * represents value for the corresponding (S) sequence of actions determines the
	 * outcome of the expense
	 */
	private class Expense {
		public List<Integer> sequence;
		public double expense;

		public Expense(List<Integer> sequence, double expense) {
			this.sequence = sequence;
			this.expense = expense;
		}

		@Override
		public String toString() {
			return "" + sequence;
		}

		@Override
		public boolean equals(Object other) {
			if (other instanceof Expense) {
				Expense ex = (Expense) other;
				if (this.expense == ex.expense) {
					for (int i = 0; i < this.sequence.size(); i++) {
						if (!ex.sequence.contains(this.sequence.get(i))) {
							return false;
						}
					}
					return true;
				}
			}
			return false;
		}

		@Override
		public int hashCode() {
			return 31 * Double.hashCode(expense) + 13 * sequence.hashCode();
		}
	}

}
